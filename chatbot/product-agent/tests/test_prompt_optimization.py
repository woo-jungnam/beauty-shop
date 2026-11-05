import pytest
from unittest.mock import MagicMock
from app.llm.client import LLMClient
from app.llm.prompts import buildUnderstandingPrompt
from app.schemas.query import Intent, PrimaryIntent, UnderstandingResult


def test_build_understanding_prompt_compact():
    msgs = buildUnderstandingPrompt(
        message="Da dầu mụn dùng kem chống nắng nào",
        currentState={"active_focus_product": "Anessa Perfect UV", "skin_type": "oily"},
    )
    assert len(msgs) == 2
    assert msgs[0]["role"] == "system"
    assert "MODULE QUERY UNDERSTANDING" in msgs[0]["content"]
    # Check that system prompt is compact (< 2500 chars, previously > 6000 chars)
    assert len(msgs[0]["content"]) < 3000
    assert "user_message" in msgs[1]["content"]
    assert "active_focus_product" in msgs[1]["content"]


def test_parse_and_validate_streamlined_response():
    client = LLMClient()

    streamlined_json = {
        "primary_intent": "TƯ_VẤN_SẢN_PHẨM",
        "intent": "PRODUCT_SEARCH",
        "target_type": "PRODUCT",
        "category": "kem chống nắng",
        "brand": "Anessa",
        "constraints": {
            "target_type": "PRODUCT",
            "category": "sunscreen",
            "brand": "Anessa",
            "skin_type": "oily",
            "max_price": 450000.0,
        },
        "preferences": {
            "concerns": ["mụn", "kiềm dầu"],
            "ingredients": ["zinc oxide"],
        },
        "negative_preferences": {
            "alcohol_free": False,
            "fragrance_free": False,
            "excluded_ingredients": [],
        },
        "semantic_query": "kem chống nắng kiềm dầu cho da dầu mụn",
        "bm25_query": "kem chống nắng kcn kiềm dầu mụn anessa",
        "needs_clarification": False,
        "clarification_question": None,
    }

    mock_resp = MagicMock()
    mock_resp.json.return_value = {
        "choices": [
            {
                "message": {
                    "content": str(streamlined_json).replace("'", '"').replace("None", "null").replace("False", "false")
                }
            }
        ]
    }

    result = client.parseAndValidateResponse(mock_resp, originalMessage="tư vấn kem chống nắng anessa cho da dầu mụn")

    assert isinstance(result, UnderstandingResult)
    assert result.primary_intent == PrimaryIntent.TU_VAN_SAN_PHAM.value
    assert result.intent == Intent.PRODUCT_SEARCH
    assert result.target_type == "PRODUCT"
    assert result.category == "kem chống nắng"
    assert result.brand == "Anessa"
    assert result.constraints.get("skin_type") == "oily"
    assert result.constraints.get("max_price") == 450000.0
    assert "mụn" in result.preferences.get("concerns", [])
    assert result.needs_clarification is False
    assert result.clarification_question is None
    # Backward-compat defaults populated
    assert result.complexity_level is not None
    assert isinstance(result.goals, list)
    assert isinstance(result.entities, dict)


def test_parse_and_validate_bare_clarification_response():
    client = LLMClient()

    bare_json = {
        "primary_intent": "KHÔNG_XÁC_ĐỊNH",
        "intent": "UNKNOWN",
        "target_type": "PRODUCT",
        "category": "serum",
        "brand": None,
        "constraints": {"target_type": "PRODUCT"},
        "preferences": {},
        "semantic_query": "serum",
        "bm25_query": "serum",
        "needs_clarification": True,
        "clarification_question": "Dạ bạn đang cần tìm serum điều trị vấn đề gì (mụn, thâm, cấp ẩm) và cho loại da nào ạ?",
    }

    mock_resp = MagicMock()
    mock_resp.json.return_value = {
        "choices": [
            {
                "message": {
                    "content": str(bare_json).replace("'", '"').replace("None", "null").replace("True", "true")
                }
            }
        ]
    }

    result = client.parseAndValidateResponse(mock_resp, originalMessage="serum")

    assert isinstance(result, UnderstandingResult)
    assert result.needs_clarification is True
    assert result.clarification_question is not None
    assert "serum" in result.clarification_question


def test_parse_and_validate_flat_booking_and_entity_sync():
    client = LLMClient()

    flat_booking_json = {
        "intent": "BOOKING",
        "target": "SERVICE",
        "category": None,
        "brand": None,
        "skin_type": None,
        "concerns": ["mụn"],
        "ingredients": [],
        "max_price": None,
        "needs_clarification": False,
        "clarification_question": None,
    }

    mock_resp = MagicMock()
    mock_resp.json.return_value = {
        "choices": [
            {
                "message": {
                    "content": str(flat_booking_json).replace("'", '"').replace("None", "null").replace("False", "false")
                }
            }
        ]
    }

    result = client.parseAndValidateResponse(mock_resp, originalMessage="đặt lịch peel da trị mụn")

    assert isinstance(result, UnderstandingResult)
    assert result.intent == Intent.BOOKING
    assert result.primary_intent == PrimaryIntent.DAT_LICH.value
    assert result.target_type == "SERVICE"
    assert result.entities.get("target_concerns") == ["mụn"]
