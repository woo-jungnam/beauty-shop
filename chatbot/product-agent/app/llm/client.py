import asyncio
import json
import logging
from collections import OrderedDict
import time
from typing import Any, Optional
import httpx
from pydantic import ValidationError

from app.core.config import Settings, getSettings, get_settings
from app.llm.prompts import buildUnderstandingPrompt, build_understanding_prompt, getUnderstandingJsonSchema, get_understanding_json_schema
from app.schemas.query import UnderstandingResult, calculateInformationScore

logger = logging.getLogger(__name__)


class QueryUnderstandingCache:

    def __init__(self, maxSize: int = 1000, ttlSeconds: float = 3600.0, **kwargs: Any):
        self.maxSize = maxSize if "maxSize" in kwargs or maxSize != 1000 else kwargs.get("max_size", maxSize)
        self.ttlSeconds = ttlSeconds if "ttlSeconds" in kwargs or ttlSeconds != 3600.0 else kwargs.get("ttl_seconds", ttlSeconds)
        self.max_size = self.maxSize
        self.ttl_seconds = self.ttlSeconds
        self._cache: OrderedDict[str, tuple[float, UnderstandingResult]] = OrderedDict()

    def makeKey(self, message: str, currentState: dict[str, Any] | None = None, **kwargs: Any) -> str:
        curState = currentState if currentState is not None else kwargs.get("current_state")
        cleanMsg = " ".join(message.strip().lower().split())
        stateRepr = ""
        if curState:
            try:
                stateRepr = json.dumps(curState, sort_keys=True, ensure_ascii=False)
            except Exception:
                stateRepr = str(curState)
        return f"{cleanMsg}__state__{stateRepr}"

    _make_key = makeKey

    def get(self, message: str, currentState: dict[str, Any] | None = None, **kwargs: Any) -> Optional[UnderstandingResult]:
        curState = currentState if currentState is not None else kwargs.get("current_state")
        key = self.makeKey(message, curState)
        if key in self._cache:
            ts, result = self._cache[key]
            if time.time() - ts < self.ttlSeconds:
                self._cache.move_to_end(key)
                return result.model_copy(deep=True)
            else:
                del self._cache[key]
        return None

    def set(
        self,
        message: str,
        currentState: dict[str, Any] | None = None,
        result: Optional[UnderstandingResult] = None,
        **kwargs: Any,
    ) -> None:
        curState = currentState if currentState is not None else kwargs.get("current_state")
        res = result if result is not None else kwargs.get("result")
        if res is None:
            return
        key = self.makeKey(message, curState)
        if key in self._cache:
            self._cache.move_to_end(key)
        self._cache[key] = (time.time(), res.model_copy(deep=True))
        if len(self._cache) > self.maxSize:
            self._cache.popitem(last=False)

    def clear(self) -> None:
        self._cache.clear()


class LLMClientError(Exception):

    def __init__(self, message: str, details: Any = None):
        super().__init__(message)
        self.message = message
        self.details = details


class LLMAuthenticationError(LLMClientError):
    pass


class LLMTimeoutError(LLMClientError):
    pass


class LLMAPIError(LLMClientError):

    def __init__(self, message: str, statusCode: int = 500, details: Any = None, **kwargs: Any):
        super().__init__(message, details)
        self.statusCode = statusCode if "statusCode" in kwargs or statusCode != 500 else kwargs.get("status_code", statusCode)
        self.status_code = self.statusCode


class LLMResponseValidationError(LLMClientError):
    pass


class LLMClient:

    def __init__(
        self,
        settings: Optional[Settings] = None,
        httpClient: Optional[httpx.AsyncClient] = None,
        **kwargs: Any,
    ):
        self.settings = settings or getSettings()
        rawClient = httpClient if httpClient is not None else kwargs.get("http_client")
        self.baseUrl = self.settings.llmBaseUrl.rstrip("/")
        self.apiKey = self.settings.llmApiKey
        self.model = self.settings.llmModel
        self.fallbackModel = getattr(self.settings, "llmFallbackModel", getattr(self.settings, "llm_fallback_model", None))
        self.maxTokens = getattr(self.settings, "llmMaxTokens", getattr(self.settings, "llm_max_tokens", 600))
        self.maxRetries = getattr(self.settings, "llmMaxRetries", getattr(self.settings, "llm_max_retries", 2))
        self.retryDelay = getattr(self.settings, "llmRetryDelay", getattr(self.settings, "llm_retry_delay", 1.0))
        self.timeout = httpx.Timeout(self.settings.llmTimeout or getattr(self.settings, "llm_timeout", 60.0))
        self.customClient = rawClient is not None
        self.httpClient = rawClient
        self.clientLoop = None

        self.base_url = self.baseUrl
        self.api_key = self.apiKey
        self.fallback_model = self.fallbackModel
        self.max_tokens = self.maxTokens
        self.max_retries = self.maxRetries
        self.retry_delay = self.retryDelay
        self._http_client = self.httpClient
        self._custom_client = self.customClient

        self._cache = QueryUnderstandingCache(
            maxSize=getattr(self.settings, "embeddingCacheSize", getattr(self.settings, "embedding_cache_size", 1000)),
            ttlSeconds=getattr(self.settings, "cacheTtlSeconds", getattr(self.settings, "cache_ttl_seconds", 3600)),
        )

    def getHttpClient(self) -> httpx.AsyncClient:
        if self.customClient and self.httpClient is not None:
            return self.httpClient

        try:
            currentLoop = asyncio.get_running_loop()
        except RuntimeError:
            currentLoop = None

        if (
            self.httpClient is None
            or self.httpClient.is_closed
            or self.clientLoop != currentLoop
        ):
            self.clientLoop = currentLoop
            self.httpClient = httpx.AsyncClient(
                timeout=self.timeout,
                limits=httpx.Limits(
                    max_keepalive_connections=20,
                    max_connections=50,
                    keepalive_expiry=60.0,
                ),
            )
            self._http_client = self.httpClient
        return self.httpClient

    _get_http_client = getHttpClient

    async def aclose(self) -> None:
        if self.httpClient is not None and not self.httpClient.is_closed:
            await self.httpClient.aclose()

    async def sendRequest(
        self,
        client: httpx.AsyncClient,
        endpoint: str,
        payload: dict[str, Any],
        headers: dict[str, str],
    ) -> httpx.Response:
        try:
            response = await client.post(endpoint, json=payload, headers=headers)
        except httpx.TimeoutException as exc:
            logger.error("Hết thời gian chờ phản hồi LLM API: %s", exc)
            raise LLMTimeoutError(
                f"Quá thời gian chờ phản hồi sau {self.settings.llmTimeout}s (timed out)"
            ) from exc
        except httpx.RequestError as exc:
            logger.error("Lỗi mạng khi kết nối tới LLM API: %s", exc)
            raise LLMClientError(
                f"Lỗi mạng khi kết nối LLM API: {str(exc)}"
            ) from exc

        if response.status_code == 400 and "response_format" in response.text:
            logger.warning("Nhà cung cấp không hỗ trợ json_schema; chuyển sang định dạng json_object")
            fallbackPayload = dict(payload)
            fallbackPayload["response_format"] = {"type": "json_object"}
            try:
                response = await client.post(endpoint, json=fallbackPayload, headers=headers)
            except httpx.TimeoutException as exc:
                raise LLMTimeoutError(
                    f"Quá thời gian chờ phản hồi sau {self.settings.llmTimeout}s (timed out)"
                ) from exc
            except httpx.RequestError as exc:
                raise LLMClientError(
                    f"Lỗi mạng khi kết nối LLM API: {str(exc)}"
                ) from exc

        return response

    _send_request = sendRequest

    @staticmethod
    def extractRetryDelay(response: httpx.Response, fallbackDelay: float = 1.0, **kwargs: Any) -> float:
        fDelay = fallbackDelay if "fallbackDelay" in kwargs or fallbackDelay != 1.0 else kwargs.get("fallback_delay", fallbackDelay)
        retryAfter = response.headers.get("Retry-After")
        if retryAfter:
            try:
                return max(1.0, float(retryAfter))
            except ValueError:
                pass
        try:
            data = response.json()
            if isinstance(data, list) and data:
                data = data[0]
            details = data.get("error", {}).get("details", [])
            for d in details:
                if isinstance(d, dict) and "retryDelay" in d:
                    sVal = str(d["retryDelay"]).rstrip("s")
                    return max(1.0, float(sVal) + 1.0)
        except Exception:
            pass
        return fDelay

    _extract_retry_delay = extractRetryDelay

    async def understand(
        self,
        message: str,
        currentState: dict[str, Any] | None = None,
        **kwargs: Any,
    ) -> UnderstandingResult:
        curState = currentState if currentState is not None else kwargs.get("current_state")
        if not self.apiKey or self.apiKey in ("your_llm_api_key_here", ""):
            raise LLMAuthenticationError("Chưa cấu hình LLM_API_KEY hợp lệ trong môi trường.")

        cached = self._cache.get(message, curState)
        if cached is not None:
            logger.info("Khớp bộ nhớ đệm NLU Understanding Cache cho tin nhắn: '%s'", message)
            return cached

        messages = buildUnderstandingPrompt(message, curState)
        endpoint = f"{self.baseUrl}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.apiKey}",
            "Content-Type": "application/json",
        }

        payload: dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.0,
            "max_tokens": 1500,
            "response_format": {"type": "json_object"},
        }

        client = self.getHttpClient()
        modelsToTry = [self.model]
        if self.fallbackModel and self.fallbackModel != self.model:
            modelsToTry.append(self.fallbackModel)

        lastResponse: Optional[httpx.Response] = None

        for modelIdx, targetModel in enumerate(modelsToTry):
            payload["model"] = targetModel
            attempts = self.maxRetries + 1

            for attempt in range(attempts):
                response = await self.sendRequest(client, endpoint, payload, headers)
                lastResponse = response

                if response.status_code in (429, 502, 503, 504):
                    if attempt < attempts - 1:
                        if response.status_code in (429, 503):
                            defaultWait = max(4.0, 4.0 * (2 ** attempt))
                        else:
                            defaultWait = self.retryDelay * (2 ** attempt)
                        waitSec = self.extractRetryDelay(response, defaultWait)
                        logger.warning(
                            "Lỗi tạm thời từ LLM (%d) trên model '%s'. Thử lại sau %.1fs (lần %d/%d)...",
                            response.status_code, targetModel, waitSec, attempt + 1, attempts
                        )
                        await asyncio.sleep(waitSec)
                        continue
                    elif modelIdx < len(modelsToTry) - 1:
                        logger.warning(
                            "Đã hết số lần thử lại trên model '%s'. Chuyển sang model dự phòng '%s'...",
                            targetModel, modelsToTry[modelIdx + 1]
                        )
                        break

                self.checkHttpStatus(response)
                try:
                    validated = self.parseAndValidateResponse(response, originalMessage=message)
                    self._cache.set(message, curState, validated)
                    return validated
                except LLMResponseValidationError as valExc:
                    if attempt < attempts - 1:
                        logger.warning("Phản hồi JSON bị lỗi (%s). Thử lại sau %.1fs...", valExc, self.retryDelay)
                        await asyncio.sleep(self.retryDelay)
                        continue
                    elif modelIdx < len(modelsToTry) - 1:
                        logger.warning("Lỗi JSON trên model '%s'. Chuyển sang model dự phòng...", targetModel)
                        break
                    raise

        if lastResponse is not None:
            self.checkHttpStatus(lastResponse)
        raise LLMClientError("Không nhận được phản hồi hợp lệ từ LLM API sau tất cả các lần thử")

    async def generateText(
        self,
        prompt: str,
        systemInstruction: str = "Bạn là trợ lý tư vấn mỹ phẩm cao cấp và tận tâm.",
        maxTokens: int = 800,
        temperature: float = 0.4,
        **kwargs: Any,
    ) -> str:
        sysInst = systemInstruction if "systemInstruction" in kwargs or systemInstruction != "Bạn là trợ lý tư vấn mỹ phẩm cao cấp và tận tâm." else kwargs.get("system_instruction", systemInstruction)
        mTokens = maxTokens if "maxTokens" in kwargs or maxTokens != 800 else kwargs.get("max_tokens", maxTokens)

        if not self.apiKey or self.apiKey in ("your_llm_api_key_here", ""):
            raise LLMAuthenticationError("Chưa cấu hình LLM_API_KEY hợp lệ trong môi trường.")

        messages = []
        if sysInst:
            messages.append({"role": "system", "content": sysInst})
        messages.append({"role": "user", "content": prompt})

        endpoint = f"{self.baseUrl}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.apiKey}",
            "Content-Type": "application/json",
        }

        payload: dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": mTokens,
        }

        client = self.getHttpClient()
        modelsToTry = [self.model]
        if self.fallbackModel and self.fallbackModel != self.model:
            modelsToTry.append(self.fallbackModel)

        lastResponse: Optional[httpx.Response] = None

        for modelIdx, targetModel in enumerate(modelsToTry):
            payload["model"] = targetModel
            attempts = self.maxRetries + 1

            for attempt in range(attempts):
                response = await self.sendRequest(client, endpoint, payload, headers)
                lastResponse = response

                if response.status_code in (429, 502, 503, 504):
                    if attempt < attempts - 1:
                        if response.status_code in (429, 503):
                            defaultWait = max(4.0, 4.0 * (2 ** attempt))
                        else:
                            defaultWait = self.retryDelay * (2 ** attempt)
                        waitSec = self.extractRetryDelay(response, defaultWait)
                        logger.warning(
                            "Lỗi tạm thời từ LLM (%d) trên model '%s'. Thử lại sau %.1fs...",
                            response.status_code, targetModel, waitSec
                        )
                        await asyncio.sleep(waitSec)
                        continue
                    elif modelIdx < len(modelsToTry) - 1:
                        break

                self.checkHttpStatus(response)
                data = response.json()
                choices = data.get("choices", [])
                if choices:
                    return choices[0].get("message", {}).get("content", "").strip()
                return ""

        if lastResponse is not None:
            self.checkHttpStatus(lastResponse)
        raise LLMClientError("Không thể sinh câu trả lời từ LLM API")

    generate_text = generateText

    async def streamText(
        self,
        prompt: str,
        systemInstruction: str = "Bạn là trợ lý tư vấn mỹ phẩm cao cấp và tận tâm.",
        maxTokens: int = 500,
        temperature: float = 0.3,
        **kwargs: Any,
    ):
        sysInst = systemInstruction if "systemInstruction" in kwargs or systemInstruction != "Bạn là trợ lý tư vấn mỹ phẩm cao cấp và tận tâm." else kwargs.get("system_instruction", systemInstruction)
        mTokens = maxTokens if "maxTokens" in kwargs or maxTokens != 500 else kwargs.get("max_tokens", maxTokens)

        if not self.apiKey or self.apiKey in ("your_llm_api_key_here", ""):
            raise LLMAuthenticationError("Chưa cấu hình LLM_API_KEY hợp lệ trong môi trường.")

        messages = []
        if sysInst:
            messages.append({"role": "system", "content": sysInst})
        messages.append({"role": "user", "content": prompt})

        endpoint = f"{self.baseUrl}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.apiKey}",
            "Content-Type": "application/json",
        }

        payload: dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": mTokens,
            "stream": True,
        }

        client = self.getHttpClient()
        modelsToTry = [self.model]
        if self.fallbackModel and self.fallbackModel != self.model:
            modelsToTry.append(self.fallbackModel)
        yieldedContent = False

        for modelIdx, targetModel in enumerate(modelsToTry):
            payload["model"] = targetModel
            for attempt in range(self.maxRetries + 1):
                try:
                    async with client.stream("POST", endpoint, json=payload, headers=headers) as resp:
                        if resp.status_code != 200:
                            await resp.aread()
                            if resp.status_code in (429, 502, 503, 504):
                                if attempt < self.maxRetries:
                                    defaultWait = max(4.0, 4.0 * 2 ** attempt) if resp.status_code in (429, 503) else self.retryDelay * 2 ** attempt
                                    waitSec = self.extractRetryDelay(resp, defaultWait)
                                    logger.warning("Streaming LLM returned HTTP %d on '%s'; retrying in %.1fs.", resp.status_code, targetModel, waitSec)
                                    await asyncio.sleep(waitSec)
                                    continue
                                if modelIdx < len(modelsToTry) - 1:
                                    break
                            self.checkHttpStatus(resp)

                        async for line in resp.aiter_lines():
                            line = line.strip()
                            if not line.startswith("data:"):
                                continue
                            dataStr = line[5:].strip()
                            if dataStr == "[DONE]":
                                break
                            try:
                                chunkData = json.loads(dataStr)
                            except json.JSONDecodeError:
                                continue
                            choices = chunkData.get("choices", [])
                            content = choices[0].get("delta", {}).get("content", "") if choices else ""
                            if content:
                                yieldedContent = True
                                yield content
                        return
                except (httpx.TimeoutException, httpx.RequestError) as exc:
                    # Retrying an interrupted answer would duplicate text already sent to the user.
                    if yieldedContent or (attempt == self.maxRetries and modelIdx == len(modelsToTry) - 1):
                        if isinstance(exc, httpx.TimeoutException):
                            raise LLMTimeoutError(f"Quá thời gian chờ khi truyền dòng LLM: {exc}") from exc
                        raise LLMClientError(f"Lỗi mạng khi truyền dòng LLM: {exc}") from exc
                    if attempt < self.maxRetries:
                        await asyncio.sleep(self.retryDelay * 2 ** attempt)
                        continue
                    break

    stream_text = streamText

    def checkHttpStatus(self, response: httpx.Response) -> None:
        status = response.status_code
        if status in (401, 403):
            raise LLMAuthenticationError(
                f"Lỗi xác thực API Key (HTTP {status}): {response.text}",
                details={"status_code": status, "body": response.text},
            )
        if 400 <= status < 500:
            raise LLMAPIError(
                f"Lỗi yêu cầu phía client (HTTP {status}): {response.text}",
                statusCode=status,
                details=response.text,
            )
        if status >= 500:
            raise LLMAPIError(
                f"Lỗi máy chủ dịch vụ LLM (HTTP {status}): {response.text}",
                statusCode=status,
                details=response.text,
            )

    _check_http_status = checkHttpStatus

    def parseAndValidateResponse(
        self,
        response: httpx.Response,
        originalMessage: str = "",
        **kwargs: Any,
    ) -> UnderstandingResult:
        origMsg = originalMessage if "originalMessage" in kwargs or originalMessage != "" else kwargs.get("original_message", originalMessage)
        try:
            responseJson = response.json()
        except Exception as exc:
            raise LLMResponseValidationError(
                f"Không thể phân giải phản hồi LLM dạng JSON: {response.text}"
            ) from exc

        choices = responseJson.get("choices")
        if not choices or not isinstance(choices, list):
            raise LLMResponseValidationError(
                "Thiếu hoặc không hợp lệ trường 'choices' trong phản hồi",
                details=responseJson,
            )

        messageObj = choices[0].get("message") or {}
        rawContent = messageObj.get("content")

        if not rawContent:
            raise LLMResponseValidationError(
                "Nội dung phản hồi của LLM bị rỗng",
                details=responseJson,
            )

        try:
            if isinstance(rawContent, str):
                cleanedContent = rawContent.strip()
                if cleanedContent.startswith("```"):
                    lines = cleanedContent.splitlines()
                    if lines[0].startswith("```"):
                        lines = lines[1:]
                    if lines and lines[-1].strip() == "```":
                        lines = lines[:-1]
                    cleanedContent = "\n".join(lines).strip()
                try:
                    parsedData = json.loads(cleanedContent)
                except json.JSONDecodeError:
                    fixedData = None
                    lastComma = cleanedContent.rfind(",")
                    if lastComma != -1:
                        for closer in ("}", "}}", "}}}", "]}}", "]}", "\n}}"):
                            try:
                                candidate = cleanedContent[:lastComma].rstrip() + closer
                                fixedData = json.loads(candidate)
                                break
                            except Exception:
                                pass
                    if fixedData is not None:
                        parsedData = fixedData
                    else:
                        raise
            elif isinstance(rawContent, dict):
                parsedData = rawContent
            else:
                raise ValueError("Nội dung không phải chuỗi JSON hay dict")
        except Exception as exc:
            raise LLMResponseValidationError(
                f"Nội dung phản hồi không phải JSON hợp lệ (not valid JSON): {rawContent}"
            ) from exc

        if isinstance(parsedData, dict) and "understanding_result" in parsedData:
            parsedData = parsedData["understanding_result"]

        if isinstance(parsedData, dict):
            if isinstance(parsedData.get("primary_intent"), dict):
                p_in = parsedData["primary_intent"]
                parsedData["primary_intent"] = str(p_in.get("primary_intent") or p_in.get("intent") or "TƯ_VẤN_SẢN_PHẨM")
            if isinstance(parsedData.get("intent"), dict):
                i_in = parsedData["intent"]
                parsedData["intent"] = str(i_in.get("intent") or i_in.get("primary_intent") or "PRODUCT_SEARCH")

            if not parsedData.get("raw_query") and origMsg:
                parsedData["raw_query"] = origMsg

            if not parsedData.get("semantic_query"):
                parts = []
                if parsedData.get("category"):
                    parts.append(str(parsedData["category"]))
                elif parsedData.get("entities", {}).get("product_category"):
                    parts.append(str(parsedData["entities"]["product_category"]))
                if parsedData.get("brand"):
                    parts.append(str(parsedData["brand"]))
                elif parsedData.get("entities", {}).get("brands"):
                    parts.append(str(parsedData["entities"]["brands"][0]))
                cDict = parsedData.get("constraints") or {}
                if cDict.get("skin_type"):
                    parts.append(f"cho da {cDict['skin_type']}")
                pDict = parsedData.get("preferences") or {}
                concerns = pDict.get("concerns") or pDict.get("concern") or []
                if isinstance(concerns, list) and concerns:
                    parts.append(" ".join(str(c) for c in concerns))
                elif isinstance(concerns, str) and concerns:
                    parts.append(concerns)
                ingrs = pDict.get("ingredients") or []
                if isinstance(ingrs, list) and ingrs:
                    parts.append(" ".join(str(i) for i in ingrs))
                nDict = parsedData.get("negative_preferences") or {}
                if nDict.get("alcohol_free"):
                    parts.append("không cồn")
                if nDict.get("fragrance_free"):
                    parts.append("không hương liệu")
                parsedData["semantic_query"] = " ".join(parts).strip() or origMsg

            if not parsedData.get("bm25_query"):
                parsedData["bm25_query"] = parsedData.get("semantic_query") or origMsg

            if not parsedData.get("complexity_level"):
                cCount = len(parsedData.get("constraints") or {})
                if parsedData.get("needs_clarification"):
                    parsedData["complexity_level"] = "MỨC_4_NHIỀU_LƯỢT_HỘI_THOẠI"
                elif str(parsedData.get("primary_intent")) in ("XÂY_DỰNG_CHU_TRINH", "PHÂN_TÍCH_CHU_TRINH", "SO_SÁNH_SẢN_PHẨM"):
                    parsedData["complexity_level"] = "MỨC_5_SUY_LUẬN_PHỨC_TẠP"
                elif cCount >= 3:
                    parsedData["complexity_level"] = "MỨC_3_NHIỀU_RÀNG_BUỘC"
                else:
                    parsedData["complexity_level"] = "MỨC_1_ĐƠN_GIẢN"

            origLower = (origMsg or "").lower()
            isExplicitRoutine = any(kw in origLower for kw in ("routine", "chu trình", "các bước", "combo"))
            if not isExplicitRoutine:
                curIntentStr = str(parsedData.get("intent", ""))
                if "ROUTINE" in curIntentStr:
                    if any(cat in origLower for cat in ("serum", "kem chống nắng", "sữa rửa mặt", "nước tẩy trang", "kem dưỡng", "toner", "nước hoa hồng", "son môi", "món nào", "sản phẩm nào", "cấp cứu")):
                        parsedData["intent"] = "PRODUCT_SEARCH"
                        if parsedData.get("primary_intent") == "XÂY_DỰNG_CHU_TRINH":
                            parsedData["primary_intent"] = "TƯ_VẤN_SẢN_PHẨM"

            spaKeywords = ("triệt lông", "peel da", "massage", "lấy nhân mụn", "soi da", "điện di", "vi kim", "spa")
            if any(k in origLower for k in spaKeywords):
                if not any(w in origLower for w in ("kèm", "combo")):
                    parsedData["target_type"] = "SERVICE"
                    if "constraints" in parsedData and isinstance(parsedData["constraints"], dict):
                        parsedData["constraints"]["target_type"] = "SERVICE"
                    if "entities" in parsedData and isinstance(parsedData["entities"], dict):
                        parsedData["entities"]["target_type"] = "SERVICE"

            # 1. Đảm bảo target_type tuyệt đối không bao giờ để null
            curTarget = parsedData.get("target_type") or parsedData.get("target")
            if not curTarget or str(curTarget).upper() in ("NONE", "NULL", ""):
                spaKeywords = ("triệt lông", "peel da", "massage", "lấy nhân mụn", "soi da", "điện di", "vi kim", "spa", "dịch vụ chăm sóc", "chăm sóc da")
                curTarget = "SERVICE" if any(k in origLower for k in spaKeywords) else "PRODUCT"
            parsedData["target_type"] = str(curTarget).upper()
            parsedData["target"] = parsedData["target_type"]

            # 2. Kiểm tra Intent có dữ liệu hợp lệ hay chưa
            pIntentStr = str(parsedData.get("primary_intent", ""))
            hasValidIntent = (
                pIntentStr not in ("KHÔNG_XÁC_ĐỊNH", "KHONG_XAC_DINH", "")
                and parsedData.get("intent") not in ("UNKNOWN",)
            )

            # 3. Kiểm tra Information Completeness Score
            infoScore = calculateInformationScore(parsedData, origLower)
            parsedData["information_score"] = infoScore

            if infoScore >= 3.0 and hasValidIntent:
                parsedData["needs_clarification"] = False
                if parsedData.get("action_routing") == "CLARIFICATION_PROMPT":
                    parsedData["action_routing"] = "CONSTRAINED_HYBRID_SEARCH"
                if isinstance(parsedData.get("dialogue_state"), dict):
                    parsedData["dialogue_state"]["needs_clarification"] = False
                    parsedData["dialogue_state"]["clarification_question"] = None
            else:
                # Nếu dưới 3.0 điểm hoặc chưa có intent hợp lệ: kích hoạt hỏi làm rõ nếu mơ hồ
                if parsedData.get("needs_clarification") or not hasValidIntent:
                    parsedData["needs_clarification"] = True
                    parsedData["action_routing"] = "CLARIFICATION_PROMPT"

            if not parsedData.get("action_routing"):
                if parsedData.get("needs_clarification"):
                    parsedData["action_routing"] = "CLARIFICATION_PROMPT"
                elif parsedData.get("complexity_level") == "MỨC_5_SUY_LUẬN_PHỨC_TẠP":
                    parsedData["action_routing"] = "CHAIN_OF_THOUGHT_REASONING"
                else:
                    parsedData["action_routing"] = "CONSTRAINED_HYBRID_SEARCH"

        try:
            return UnderstandingResult.model_validate(parsedData)
        except ValidationError as exc:
            raise LLMResponseValidationError(
                f"Phản hồi LLM không vượt qua kiểm định schema (failed UnderstandingResult Pydantic schema validation): {exc.errors()}",
                details=exc.errors(),
            ) from exc

    _parse_and_validate_response = parseAndValidateResponse


llmClient = LLMClient()
llm_client = llmClient
