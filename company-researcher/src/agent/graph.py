import asyncio
from typing import cast, Any, Literal
import json
import os

from tavily import AsyncTavilyClient
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.rate_limiters import InMemoryRateLimiter
from langchain_core.runnables import RunnableConfig
from langgraph.graph import START, END, StateGraph
from pydantic import BaseModel, Field

from agent.configuration import Configuration
from agent.state import InputState, OutputState, OverallState
from agent.utils import deduplicate_sources, format_sources, format_all_notes
from agent.prompts import (
    EXTRACTION_PROMPT,
    REFLECTION_PROMPT,
    INFO_PROMPT,
    QUERY_WRITER_PROMPT,
)
from agent.schema_converter import schema_to_pydantic


rate_limiter = InMemoryRateLimiter(
    requests_per_second=4,
    check_every_n_seconds=0.1,
    max_bucket_size=10,
)

gemini_model = ChatGoogleGenerativeAI(
    model="gemini-2.5-flash",
    temperature=0,
)

tavily_async_client = AsyncTavilyClient()

class Queries(BaseModel):
    queries: list[str] = Field(description="List of search queries.")

class ReflectionOutput(BaseModel):
    is_satisfactory: bool = Field(description="True if all required fields are well populated, False otherwise")
    missing_fields: list[str] = Field(description="List of field names that are missing or incomplete")
    search_queries: list[str] = Field(description="If is_satisfactory is False, provide 1-3 targeted search queries to find the missing information")
    reasoning: str = Field(description="Brief explanation of the assessment")

# === Nodes ===

def generate_queries(state: OverallState, config: RunnableConfig) -> dict[str, Any]:
    configurable = Configuration.from_runnable_config(config)
    max_search_queries = configurable.max_search_queries

    structured_llm = gemini_model.with_structured_output(Queries)

    query_instructions = QUERY_WRITER_PROMPT.format(
        company=state.company,
        info=json.dumps(state.extraction_schema, indent=2),
        user_notes=state.user_notes,
        max_search_queries=max_search_queries,
    )

    results = cast(
        Queries,
        structured_llm.invoke(
            [
                {"role": "system", "content": query_instructions},
                {
                    "role": "user",
                    "content": "Please generate a list of search queries related to the schema that you want to populate.",
                },
            ]
        ),
    )

    return {"search_queries": results.queries}


async def research_company(state: OverallState, config: RunnableConfig) -> dict[str, Any]:
    configurable = Configuration.from_runnable_config(config)
    max_search_results = configurable.max_search_results

    search_tasks = [
        tavily_async_client.search(
            query,
            max_results=max_search_results,
            include_raw_content=True,
            topic="general",
        ) for query in (state.search_queries or [])
    ]

    search_docs = await asyncio.gather(*search_tasks)

    #for doc in search_docs:
     #   print("\n🔎 Tavily raw result:", doc)

    deduplicated_search_docs = deduplicate_sources(search_docs)
    source_str = format_sources(
        deduplicated_search_docs, max_tokens_per_source=1000, include_raw_content=True
    )
    # print("\n📄 Formatted source content being passed to Gemini:\n", source_str)

    p = INFO_PROMPT.format(
        info=json.dumps(state.extraction_schema, indent=2),
        content=source_str,
        company=state.company,
        user_notes=state.user_notes,
    )
    result = await gemini_model.ainvoke(p)
    print("\n Result from Gemini:\n", result.content)
    state_update = {
        "completed_notes": [str(result.content)],
    }

    if configurable.include_search_results:
        state_update["search_results"] = cast(list[str], deduplicated_search_docs)

    return state_update


def gather_notes_extract_schema(state: OverallState) -> dict[str, Any]:
    notes = format_all_notes(state.completed_notes)

    system_prompt = EXTRACTION_PROMPT.format(
        info=json.dumps(state.extraction_schema, indent=2),
        notes=notes
    )

    DynamicSchema = schema_to_pydantic("CompanyInfo", state.extraction_schema)
    structured_llm = gemini_model.with_structured_output(DynamicSchema)

    result = structured_llm.invoke(
        [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": "Produce a structured output from these notes.",
            },
        ]
    )
    return {"info": result.dict()}


def reflection(state: OverallState) -> dict[str, Any]:
    DynamicSchema = schema_to_pydantic("CompanyInfo", state.extraction_schema)
    structured_llm = gemini_model.with_structured_output(ReflectionOutput)

    system_prompt = REFLECTION_PROMPT.format(
        schema=json.dumps(state.extraction_schema, indent=2),
        info=json.dumps(state.info, indent=2),
    )

    result = cast(
        ReflectionOutput,
        structured_llm.invoke(
            [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": "Produce a structured reflection output."},
            ]
        ),
    )

    if result.is_satisfactory:
        return {"is_satisfactory": result.is_satisfactory}
    else:
        return {
            "is_satisfactory": result.is_satisfactory,
            "missing_fields": result.missing_fields,
            "search_queries": result.search_queries,
            "reflection_steps_taken": state.reflection_steps_taken + 1,
        }


def route_from_reflection(state: OverallState, config: RunnableConfig) -> Literal[END, "research_company"]:
    configurable = Configuration.from_runnable_config(config)

    if state.is_satisfactory:
        return END

    if state.reflection_steps_taken <= configurable.max_reflection_steps:
        return "research_company"

    return END


# === Graph Definition ===

builder = StateGraph(
    OverallState,
    input=InputState,
    output=OutputState,
    config_schema=Configuration,
)

builder.add_node("generate_queries", generate_queries)
builder.add_node("research_company", research_company)
builder.add_node("gather_notes_extract_schema", gather_notes_extract_schema)
builder.add_node("reflection", reflection)

builder.add_edge(START, "generate_queries")
builder.add_edge("generate_queries", "research_company")
builder.add_edge("research_company", "gather_notes_extract_schema")
builder.add_edge("gather_notes_extract_schema", "reflection")
builder.add_conditional_edges("reflection", route_from_reflection)

graph = builder.compile()

from langgraph.graph import StateGraph
from langgraph.constants import END

# Phase 1: Research
async def run_research(input: InputState) -> dict:
    return await research_company.invoke(input)

# Phase 2: Extraction
async def run_extraction(input: InputState) -> dict:
    return await gather_notes_extract_schema.invoke(input)

# Phase 3: Reflection
async def run_reflection(input: InputState) -> dict:
    return await reflection.invoke(input)
