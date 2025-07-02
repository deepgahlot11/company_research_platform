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
import uvicorn  # only used when we run python main.py directly

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
app = FastAPI(title="Company‑Research LangGraph API")

ENV = os.getenv("ENV", "local").lower()

ALLOWED_ORIGINS_RENDER = [
    "https://react-frontend-k26s.onrender.com",     # <-- update with real URL
    "https://spring-boot-backend-8qlb.onrender.com",
]
ALLOWED_ORIGINS_LOCAL = [
    "http://localhost:8080",  # React dev
    "http://localhost:8085",  # Spring Boot
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS_RENDER if ENV == "render" else ALLOWED_ORIGINS_LOCAL,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --------------------------------------------------------------------------- #
# Health‑check (Render hits "/" automatically)
# --------------------------------------------------------------------------- #
@app.get("/", tags=["health"])
async def root():
    return {"status": "ok", "env": ENV}


# --------------------------------------------------------------------------- #
# Models
# --------------------------------------------------------------------------- #
class AnalyzeRequest(BaseModel):
    company: str
    extraction_schema: Optional[dict] = None
    user_notes: Optional[str] = ""


# --------------------------------------------------------------------------- #
# SSE helper
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


SECURITY_HEADER = os.getenv("SECRET_KEY", "spring-secret-key")


# --------------------------------------------------------------------------- #
# Fire the entire LangGraph in one shot
# --------------------------------------------------------------------------- #
@app.post("/analyze", tags=["analyze"])
async def analyze(request: AnalyzeRequest, raw_request: Request):
    if raw_request.headers.get("x-internal-key") != SECURITY_HEADER:
        raise HTTPException(status_code=401, detail="Unauthorized")

    inputs = InputState(
        company=request.company,
        extraction_schema=request.extraction_schema or DEFAULT_EXTRACTION_SCHEMA,
        user_notes=request.user_notes or "",
    )
    return await graph.ainvoke(inputs)


# --------------------------------------------------------------------------- #
# Streaming endpoint (Server‑Sent Events)
# --------------------------------------------------------------------------- #
@app.get("/analyze/stream", tags=["analyze"])
async def analyze_stream(
    raw_request: Request,
    company: str,
    extraction_schema: Optional[str] = Query(default=None),
    user_notes: Optional[str] = Query(default=""),
):
    if raw_request.headers.get("x-internal-key") != SECURITY_HEADER:
        raise HTTPException(status_code=401, detail="Unauthorized")

    # Parse / fallback schema
    if extraction_schema:
        try:
            schema = json.loads(extraction_schema)
            if not isinstance(schema, dict):
                raise ValueError("extraction_schema must be a JSON object")
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"Bad schema JSON: {exc}") from exc
    else:
        schema = DEFAULT_EXTRACTION_SCHEMA

    base_state = OverallState(company=company, extraction_schema=schema, user_notes=user_notes)

    async def event_generator():
        try:
            yield sse("Starting analysis …")

            # 1. Generate queries
            yield sse("Generating search queries …")
            q_res = generate_queries(base_state, {})
            queries = q_res["search_queries"]
            yield sse("Queries generated.", extra={"queries": queries})

            # 2. Research
            yield sse("Researching via Tavily …")
            st_q = OverallState(**{**base_state.__dict__, "search_queries": queries})
            r_res = await research_company(st_q, {})
            yield sse(
                f"Research complete — {len(r_res.get('search_results', []))} docs.",
                extra={"result_count": len(r_res.get("search_results", []))},
            )

            # 3. Extraction
            ex_input = OverallState(**{**st_q.__dict__, **r_res})
            yield sse("Extracting structured info …")
            ex_res = gather_notes_extract_schema(ex_input)
            yield sse("Extraction done.", extra={"fields": list(ex_res['info'].keys())})

            # 4. Reflection
            ref_input = OverallState(**{**ex_input.__dict__, **ex_res})
            yield sse("Running reflection …")
            ref_res = reflection(ref_input)
            if ref_res.get("is_satisfactory"):
                yield sse("Reflection passed ✔", type_="success")
            else:
                yield sse(
                    "Reflection failed",
                    type_="warning",
                    extra={"missing": ref_res.get("missing_fields", [])},
                )

            # 5. Done
            yield sse("Analysis complete 🎉", type_="complete", extra={"result": ex_res})
            await asyncio.sleep(0.05)

        except Exception as exc:
            yield sse(f"Error: {exc}", type_="error")

    return StreamingResponse(event_generator(), media_type="text/event-stream", headers={"Cache-Control": "no-cache"})


# --------------------------------------------------------------------------- #
# Debug single‑phase endpoints (optional)
# --------------------------------------------------------------------------- #
@app.post("/analyze/research", include_in_schema=False)
async def debug_research(req: AnalyzeRequest):
    st = OverallState(
        company=req.company,
        extraction_schema=req.extraction_schema or DEFAULT_EXTRACTION_SCHEMA,
        user_notes=req.user_notes,
    )
    return await research_company(st, {})


@app.post("/analyze/extract", include_in_schema=False)
async def debug_extract(state: dict):
    return gather_notes_extract_schema(OverallState(**state))


@app.post("/analyze/reflect", include_in_schema=False)
async def debug_reflect(state: dict):
    return reflection(OverallState(**state))


# --------------------------------------------------------------------------- #
# Entrypoint when this file is executed directly (e.g. local `python main.py`)
# --------------------------------------------------------------------------- #
if __name__ == "__main__":  # pragma: no cover
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("agent.main:app", host="0.0.0.0", port=port, reload=False)