from pydantic import BaseModel, create_model
from typing import List, Dict, Any, Type, Tuple, get_args, get_origin

# Map JSON types to Python types
PYTHON_TYPE_MAP = {
    "int": int,
    "integer": int,
    "str": str,
    "string": str,
    "float": float,
    "number": float,
    "bool": bool,
    "boolean": bool,
    "list": list,
    "array": list,
    "dict": dict,
    "object": dict,
}


def resolve_type(type_str: str) -> Any:
    return PYTHON_TYPE_MAP.get(type_str.lower(), str)


def schema_to_pydantic(model_name: str, schema: dict) -> Type[BaseModel]:
    """
    Convert a schema into a dynamic Pydantic BaseModel.
    Supports:
    - Flat format: { "field1": "str", "field2": "int" }
    - JSON Schema format: { "type": "object", "properties": { ... } }
    Recursively handles nested objects and arrays of objects.
    """

    fields: Dict[str, Tuple[Any, ...]] = {}

    def build_field(name: str, prop: dict) -> Any:
        prop_type = prop.get("type", "string").lower()

        # Handle arrays
        if prop_type == "array":
            item_schema = prop.get("items", {})
            item_type = item_schema.get("type", "string")

            if item_type == "object" and "properties" in item_schema:
                # Recursively build sub-model for array items
                sub_model = schema_to_pydantic(f"{model_name}_{name.capitalize()}Item", {
                    "type": "object",
                    "properties": item_schema["properties"]
                })
                return List[sub_model]
            else:
                return List[resolve_type(item_type)]

        # Handle objects
        elif prop_type == "object" and "properties" in prop:
            sub_model = schema_to_pydantic(f"{model_name}_{name.capitalize()}Sub", prop)
            return sub_model

        # Primitive type
        return resolve_type(prop_type)

    if "type" in schema and schema["type"] == "object" and "properties" in schema:
        # JSON Schema format
        for key, prop in schema["properties"].items():
            if isinstance(prop, dict):
                fields[key] = (build_field(key, prop), ...)
            else:
                raise TypeError(f"Expected object for field '{key}', got {type(prop)}")
    else:
        # Flat format
        for key, type_str in schema.items():
            if not isinstance(type_str, str):
                raise TypeError(f"Expected string type for field '{key}', got {type(type_str)}")
            fields[key] = (resolve_type(type_str), ...)

    return create_model(model_name, **fields)
