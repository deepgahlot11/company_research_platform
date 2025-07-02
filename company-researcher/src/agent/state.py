from dataclasses import dataclass, field
from typing import Any, Optional, Annotated
import operator


DEFAULT_EXTRACTION_SCHEMA = {
    "title": "CompanyInfo",
    "description": "Basic information about a company",
    "type": "object",
    "properties": {
        "company_name": {
            "type": "string",
            "description": "Official name of the company",
        },
        "founding_year": {
            "type": "integer",
            "description": "Year the company was founded",
        },
        "founder_names": {
            "type": "array",
            "items": {"type": "string"},
            "description": "Names of the founding team members",
        },
        "product_description": {
            "type": "string",
            "description": "Brief description of the company's main product or service",
        },
        "funding_summary": {
            "type": "string",
            "description": "Summary of the company's funding history",
        },
    },
    "required": ["company_name"],
}


@dataclass(kw_only=True)
class InputState:
    """
    InputState: Defines the initial input from the user or API.
    This is passed to the graph or individual phase endpoints.
    """
    company: str
    extraction_schema: dict[str, Any] = field(default_factory=lambda: DEFAULT_EXTRACTION_SCHEMA)
    user_notes: Optional[str] = field(default=None)

    # Add these optional fields for use in stream steps
    search_queries: Optional[list[str]] = field(default=None)
    search_results: Optional[list[dict]] = field(default=None)
    completed_notes: list[str] = field(default_factory=list)
    info: Optional[dict[str, Any]] = field(default=None)
    is_satisfactory: Optional[bool] = field(default=None)
    reflection_steps_taken: int = field(default=0)


@dataclass(kw_only=True)
class OverallState:
    """
    OverallState: Tracks the evolving state of the LangGraph agent across all phases.
    It is updated and carried between nodes in the graph.
    """
    company: str
    extraction_schema: dict[str, Any] = field(default_factory=lambda: DEFAULT_EXTRACTION_SCHEMA)
    user_notes: Optional[str] = field(default=None)

    search_queries: Optional[list[str]] = field(default=None)
    search_results: Optional[list[dict]] = field(default=None)

    completed_notes: Annotated[list[str], operator.add] = field(default_factory=list)
    info: Optional[dict[str, Any]] = field(default=None)

    is_satisfactory: Optional[bool] = field(default=None)
    reflection_steps_taken: int = field(default=0)


@dataclass(kw_only=True)
class OutputState:
    """
    OutputState: Final result that will be returned to the API caller
    once the LangGraph has completed its execution.
    """
    info: dict[str, Any]
    search_results: Optional[list[dict]] = field(default=None)
