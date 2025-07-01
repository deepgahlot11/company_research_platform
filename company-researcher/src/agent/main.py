# ==== src/agent/main.py ====

from __future__ import annotations

import asyncio
import json
import os
from datetime import datetime
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from agent.graph import (
    graph,
    generate_queries,
    research_company,
    gather_notes_extract_schema,
    reflection,
)
from agent.state import DEFAULT_EXTRACTION_SCHEMA, InputState, OverallState

# --------------------------------------------------------------------------- #
# FastAPI app & CORS
# --------------------------------------------------------------------------- #
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:8085"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --------------------------------------------------------------------------- #
# Models
# --------------------------------------------------------------------------- #
class AnalyzeRequest(BaseModel):
    company: str
    extraction_schema: Optional[dict] = None
    user_notes: Optional[str] = ""


# --------------------------------------------------------------------------- #
# Small helper for SSE messages
# --------------------------------------------------------------------------- #
def sse(msg: str, *, type_: str = "update", extra: Optional[dict] = None) -> str:
    payload: dict = {
        "type": type_,
        "timestamp": datetime.utcnow().isoformat(timespec="seconds") + "Z",
        "message": msg,
    }
    if extra:
        payload.update(extra)
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


# --------------------------------------------------------------------------- #
# 1️⃣  Simple POST that runs the *whole* graph at once
# --------------------------------------------------------------------------- #
@app.post("/analyze")
async def analyze(request: AnalyzeRequest, raw_request: Request):
    if raw_request.headers.get("x-internal-key") != os.getenv("SECRET_KEY"):
        raise HTTPException(status_code=401, detail="Unauthorized")

    inputs = InputState(
        company=request.company,
        extraction_schema=request.extraction_schema or DEFAULT_EXTRACTION_SCHEMA,
        user_notes=request.user_notes or "",
    )
    try:
        return await graph.ainvoke(inputs)
    except Exception as exc:  # noqa: BLE001
        return {
            "error": str(exc),
            "hint": "Make sure schema is valid and matches expected types like str/int.",
        }


# --------------------------------------------------------------------------- #
# 2️⃣  Streaming endpoint (Server‑Sent Events)
# --------------------------------------------------------------------------- #
@app.get("/analyze/stream")
async def analyze_stream(
    raw_request: Request,
    company: str,
    extraction_schema: Optional[str] = Query(default=None),
    user_notes: Optional[str] = Query(default=""),
):
    print("Incoming security header value: ",raw_request.headers.get("x-internal-key"))
    if raw_request.headers.get("x-internal-key") != os.getenv("SECRET_KEY"):
        raise HTTPException(status_code=401, detail="Unauthorized")
    # print("Incoming /analyze/stream request:", dict(raw_request.query_params))
    # ----- decode schema (if provided) ------------------------------------- #
    if extraction_schema:
        try:
            schema = json.loads(extraction_schema)
            if not isinstance(schema, dict):
                raise ValueError("must be a JSON object")
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(
                status_code=400, detail=f"Invalid extraction_schema JSON: {exc}"
            ) from exc
    else:
        schema = DEFAULT_EXTRACTION_SCHEMA

    base_state = OverallState(
        company=company,
        extraction_schema=schema,
        user_notes=user_notes or "",
    )

    async def event_generator():
        try:
            # ––– 1. queries –––
            yield sse("Starting analysis …")
            yield sse("Generating search queries …")
            q_result = generate_queries(base_state, {})
            queries = q_result["search_queries"]
            yield sse("Queries generated.", extra={"queries": queries})
            # print("Generated queries:", queries)

            # ––– 2. research –––
            yield sse("Calling Tavily API ⏳ …")
            st_with_q = OverallState(**{**base_state.__dict__, "search_queries": queries})
            r_result = await research_company(st_with_q, {})
            found = len(r_result.get("search_results", []))
            yield sse(f"Research complete — {found} documents found.", extra={"result_count": found})

            # ––– 3. extraction –––
            ex_input = OverallState(**{**st_with_q.__dict__, **r_result})
            yield sse("Extracting structured fields …")
            ex_result = gather_notes_extract_schema(ex_input)
            fields = list(ex_result["info"].keys())
            yield sse("Extraction done.", extra={"fields": fields})

            # ––– 4. reflection –––
            ref_input = OverallState(**{**ex_input.__dict__, **ex_result})
            yield sse("Running reflection …")
            ref_result = reflection(ref_input)

            if ref_result.get("is_satisfactory"):
                yield sse("Reflection passed ✅.", type_="success")
            else:
                missing = ", ".join(ref_result.get("missing_fields", []))
                yield sse(f"Reflection failed — missing: {missing}", type_="warning")

            # ––– 5. finish –––
            yield sse(
                "Analysis complete 🎉",
                type_="complete",
                extra={"result": {"info": ex_result["info"]}},
            )
            await asyncio.sleep(0.05)

        except Exception as exc:  # noqa: BLE001
            yield sse(f"Error: {exc}", type_="error")

    headers = {"Cache-Control": "no-cache"}
    return StreamingResponse(event_generator(), media_type="text/event-stream", headers=headers)


# --------------------------------------------------------------------------- #
# 3️⃣  Optional single‑phase debug endpoints
# --------------------------------------------------------------------------- #
@app.post("/analyze/research")
async def debug_research(req: AnalyzeRequest):
    st = OverallState(
        company=req.company,
        extraction_schema=req.extraction_schema or DEFAULT_EXTRACTION_SCHEMA,
        user_notes=req.user_notes or "",
    )
    return await research_company(st, {})


@app.post("/analyze/extract")
async def debug_extract(state: dict):
    return gather_notes_extract_schema(OverallState(**state))


@app.post("/analyze/reflect")
async def debug_reflect(state: dict):
    return reflection(OverallState(**state))
