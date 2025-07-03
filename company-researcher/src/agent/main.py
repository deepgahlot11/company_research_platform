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
import uvicorn  # only used when running directly

from agent.graph import (
    graph,
    generate_queries,
    research_company,
    gather_notes_extract_schema,
    reflection,
)
from agent.state import DEFAULT_EXTRACTION_SCHEMA, InputState, OverallState

# --------------------------------------------------------------------------- #
# FastAPI App Setup
# --------------------------------------------------------------------------- #

app = FastAPI(title="Company‑Research LangGraph API")

ENV = os.getenv("ENV", "local").lower()

ALLOWED_ORIGINS_RENDER = [
    "https://react-frontend-k26s.onrender.com",
    "https://spring-boot-backend-8qlb.onrender.com",
]
ALLOWED_ORIGINS_LOCAL = [
    "http://localhost:8080",
    "http://localhost:8085",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS_RENDER if ENV == "render" else ALLOWED_ORIGINS_LOCAL,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --------------------------------------------------------------------------- #
# Health Check & Render Wake-Up
# --------------------------------------------------------------------------- #

@app.get("/", tags=["health"])
@app.get("/ping", tags=["health"])
async def ping():
    return {"status": "ok", "env": ENV}

# --------------------------------------------------------------------------- #
# Models
# --------------------------------------------------------------------------- #

class AnalyzeRequest(BaseModel):
    company: str
    extraction_schema: Optional[dict] = None
    user_notes: Optional[str] = ""

# --------------------------------------------------------------------------- #
# SSE Helper
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
# Full LangGraph POST
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
# Streaming SSE Endpoint
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

    try:
        schema = json.loads(extraction_schema) if extraction_schema else DEFAULT_EXTRACTION_SCHEMA
        if not isinstance(schema, dict):
            raise ValueError("Schema must be a JSON object")
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Invalid JSON for extraction_schema: {exc}")

    base_state = OverallState(company=company, extraction_schema=schema, user_notes=user_notes)

    async def event_generator():
        try:
            yield sse("Starting analysis …")

            yield sse("Generating search queries …")
            q_res = generate_queries(base_state, {})
            queries = q_res["search_queries"]
            yield sse("Queries generated.", extra={"queries": queries})

            yield sse("Researching via Tavily …")
            st_q = OverallState(**{**base_state.__dict__, "search_queries": queries})
            r_res = await research_company(st_q, {})
            yield sse(f"Research complete — {len(r_res.get('search_results', []))} docs.",
                      extra={"result_count": len(r_res.get("search_results", []))})

            ex_input = OverallState(**{**st_q.__dict__, **r_res})
            yield sse("Extracting structured info …")
            ex_res = gather_notes_extract_schema(ex_input)
            yield sse("Extraction done.", extra={"fields": list(ex_res['info'].keys())})

            ref_input = OverallState(**{**ex_input.__dict__, **ex_res})
            yield sse("Running reflection …")
            ref_res = reflection(ref_input)
            if ref_res.get("is_satisfactory"):
                yield sse("Reflection passed ✔", type_="success")
            else:
                yield sse("Reflection failed", type_="warning",
                          extra={"missing": ref_res.get("missing_fields", [])})

            yield sse("Analysis complete 🎉", type_="complete", extra={"result": ex_res})
            await asyncio.sleep(0.05)

        except Exception as exc:
            yield sse(f"Error: {exc}", type_="error")

    return StreamingResponse(event_generator(), media_type="text/event-stream", headers={"Cache-Control": "no-cache"})

# --------------------------------------------------------------------------- #
# Optional Debug Routes
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
# Local Entry Point
# --------------------------------------------------------------------------- #

if __name__ == "__main__":  # pragma: no cover
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("agent.main:app", host="0.0.0.0", port=port, reload=False)
