from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
from agent.graph import graph
from agent.state import InputState, DEFAULT_EXTRACTION_SCHEMA

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:8085"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

import os

class AnalyzeRequest(BaseModel):
    company: str
    extraction_schema: Optional[dict] = None
    user_notes: Optional[str] = ""

@app.post("/analyze")
async def analyze(request: AnalyzeRequest, raw_request: Request):
    auth = raw_request.headers.get("x-internal-key")
    if auth != os.getenv("SECRET_KEY"):
        raise HTTPException(status_code=401, detail="Unauthorized")

    extraction_schema = request.extraction_schema or DEFAULT_EXTRACTION_SCHEMA
    user_notes = request.user_notes or ""

    try:
        inputs = {
            "company": request.company,
            "extraction_schema": extraction_schema,
            "user_notes": user_notes,
        }
        result = await graph.ainvoke(InputState(**inputs))
        return result
    except Exception as e:
        return {
            "error": str(e),
            "hint": "Make sure schema is valid and matches expected types like str/int."
        }
