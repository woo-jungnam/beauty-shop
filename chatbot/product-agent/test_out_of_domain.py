from app.schemas.query import UnderstandingResult, Intent, PrimaryIntent
from app.services.agent.router import fastPathParser, queryRouter
from app.services.agent.dialogue import conversationManager

def test_out_of_domain():
    ctx = conversationManager.getOrCreate("test_ood_session")

    test_cases = [
        "1+1",
        "1 + 1",
        "1+1=?",
        "2*3",
        "10 / 2",
        "thời tiết hôm nay",
        "viết code python giúp tôi",
    ]

    for query in test_cases:
        res = fastPathParser.tryParse(query, ctx)
        assert res is not None, f"FastPathParser failed to catch OOD: {query}"
        assert res.intent == Intent.OUT_OF_DOMAIN, f"Expected OUT_OF_DOMAIN, got {res.intent} for {query}"
        assert res.primary_intent == PrimaryIntent.NGOAI_PHAM_VI.value

        # Router should not retrieve
        assert not queryRouter.shouldRetrieve(res), f"QueryRouter should not retrieve for {query}"

        # Non-retrieval reply
        reply = queryRouter.handleNonRetrieval(res)
        assert reply is not None, f"Expected canned reply for {query}"
        assert "ngoài phạm vi" in reply.lower() or "chuyên biệt" in reply.lower()
        print(f"[PASSED] Query: '{query}' -> Non-retrieval reply: {reply[:60]}...")

    # Test via UnderstandingResult model validation (simulating LLM output)
    llm_simulated = UnderstandingResult.model_validate({
        "raw_query": "1+1",
        "primary_intent": "NGOÀI_PHẠM_VI",
        "intent": "OUT_OF_DOMAIN",
    })
    assert llm_simulated.intent == Intent.OUT_OF_DOMAIN
    assert not queryRouter.shouldRetrieve(llm_simulated)
    assert "ngoài phạm vi" in queryRouter.handleNonRetrieval(llm_simulated).lower()
    print("[PASSED] LLM-simulated OOD validation passed.")

if __name__ == "__main__":
    test_out_of_domain()
