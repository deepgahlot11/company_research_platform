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
    """Input state defines the interface between the graph and the user (external API)."""

    company: str
    extraction_schema: dict[str, Any] = field(default_factory=lambda: DEFAULT_EXTRACTION_SCHEMA)
    user_notes: Optional[str] = field(default=None)


@dataclass(kw_only=True)
class OverallState:
    """Full state passed through the graph including intermediate values."""

    company: str
    extraction_schema: dict[str, Any] = field(default_factory=lambda: DEFAULT_EXTRACTION_SCHEMA)
    user_notes: Optional[str] = field(default=None)

    search_queries: list[str] = field(default=None)
    search_results: list[dict] = field(default=None)

    completed_notes: Annotated[list[str], operator.add] = field(default_factory=list)
    info: dict[str, Any] = field(default=None)

    is_satisfactory: bool = field(default=None)
    reflection_steps_taken: int = field(default=0)


@dataclass(kw_only=True)
class OutputState:
    """The response object for the end user."""

    info: dict[str, Any]
    search_results: list[dict] = field(default=None)
