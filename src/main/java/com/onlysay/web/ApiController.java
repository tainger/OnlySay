package com.onlysay.web;

import com.onlysay.config.OnlySayProperties;
import com.onlysay.intent.IntentRecognizer;
import com.onlysay.intent.IntentResult;
import com.onlysay.intent.IntentTrace;
import com.onlysay.intent.IntentType;
import com.onlysay.service.GenerateService;
import com.onlysay.service.IngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Web API 入口（替代旧 ApiServer 的 Javalin 路由）。
 * 端点（路径保持不变，前端零改动）：
 *   POST /api/ingest        → 录入博主风格样本
 *   POST /api/generate      → 前置意图路由：创作/改写走 RAG 生成，澄清返回反问，其余意图返回占位
 *   POST /api/intent        → 纯意图识别调试（不触发下游执行），响应含 trace
 *   GET  /api/intent/stats  → 识别指标：各层命中率、兜底率、澄清率、P95/P99、per-intent 命中计数
 *   GET  /api/health        → 健康检查
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final IngestService ingestService;
    private final GenerateService generateService;
    private final IntentRecognizer intentRecognizer;
    private final OnlySayProperties props;

    public ApiController(IngestService ingestService,
                         GenerateService generateService,
                         IntentRecognizer intentRecognizer,
                         OnlySayProperties props) {
        this.ingestService = ingestService;
        this.generateService = generateService;
        this.intentRecognizer = intentRecognizer;
        this.props = props;
        log.info("🧭 意图识别漏斗已就绪（enabled={}, classifier={}）",
                props.getIntent().isEnabled(), props.getIntent().getClassifier());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "OnlySay RAG API 运行中");
        return result;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest() {
        String samplesPath = "samples/blogger.md";
        ingestService.ingestSamples(samplesPath);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "样本录入完成");
        result.put("totalRecords", ingestService.getRecordCount());
        return result;
    }

    /**
     * 前置意图路由 + 同风格文案生成
     * Body: { "userInput": "...", "sessionId": "可选" }
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, String> body) {
        String userInput = body.get("userInput");
        String sessionId = body.get("sessionId");

        if (userInput == null || userInput.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "userInput 不能为空");
        }

        // ===== 前置意图路由（intent.enabled=false 时跳过，行为与旧版一致）=====
        if (props.getIntent().isEnabled()) {
            IntentRecognizer.Recognition recognition = intentRecognizer.recognize(userInput, sessionId);
            IntentResult intentResult = recognition.result();
            IntentType intent = intentResult.getIntent();

            // 澄清反问：不进入生成
            if (intent == IntentType.CLARIFICATION) {
                Map<String, Object> response = baseResponse(intentResult, recognition.trace());
                response.put("success", true);
                response.put("clarification", true);
                response.put("message", intentResult.getClarificationText());
                return ResponseEntity.ok(response);
            }

            // 已识别但暂不支持的业务意图：占位响应
            if (intent != IntentType.CONTENT_GENERATION && intent != IntentType.REWRITE) {
                Map<String, Object> response = baseResponse(intentResult, recognition.trace());
                response.put("success", false);
                response.put("message", "已识别意图「" + intent + "」，但该能力暂不支持，敬请期待");
                return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
            }

            // 创作意图：先查样本库，再走 RAG
            if (ingestService.getRecordCount() == 0) {
                Map<String, Object> response = baseResponse(intentResult, recognition.trace());
                response.put("success", false);
                response.put("message", "请先录入样本（调用 /api/ingest）");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            GenerateService.GenerateResult result = generateService.generateWithDetails(userInput);
            Map<String, Object> response = baseResponse(intentResult, recognition.trace());
            response.put("success", true);
            response.put("generatedText", result.getGeneratedText());
            response.put("retrievedSamples", result.getRetrievedSamples());
            return ResponseEntity.ok(response);
        }

        // ===== 旧版直通路径（intent.enabled=false）=====
        if (ingestService.getRecordCount() == 0) {
            return error(HttpStatus.BAD_REQUEST, "请先录入样本（调用 /api/ingest）");
        }

        GenerateService.GenerateResult result = generateService.generateWithDetails(userInput);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("generatedText", result.getGeneratedText());
        response.put("retrievedSamples", result.getRetrievedSamples());
        return ResponseEntity.ok(response);
    }

    /**
     * 纯意图识别调试（不触发下游执行）
     * Body: { "userInput": "...", "sessionId": "可选" }
     */
    @PostMapping("/intent")
    public ResponseEntity<Map<String, Object>> intent(@RequestBody Map<String, String> body) {
        if (!props.getIntent().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(errorBody("意图识别模块已关闭（intent.enabled=false）"));
        }
        String userInput = body.get("userInput");
        String sessionId = body.get("sessionId");

        if (userInput == null || userInput.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "userInput 不能为空");
        }

        IntentRecognizer.Recognition recognition = intentRecognizer.recognize(userInput, sessionId);
        Map<String, Object> response = baseResponse(recognition.result(), recognition.trace());
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/intent/stats")
    public Map<String, Object> intentStats() {
        return intentRecognizer.getMetrics().snapshot();
    }

    /** 公共响应字段：intent/slots/confidence/hitLayer/secondaryIntents/clarificationText/trace */
    private Map<String, Object> baseResponse(IntentResult intentResult, IntentTrace trace) {
        Map<String, Object> response = new HashMap<>();
        response.put("intent", intentResult.getIntent().name());
        response.put("slots", intentResult.getSlots());
        response.put("confidence", intentResult.getConfidence());
        response.put("hitLayer", intentResult.getHitLayer().name());
        response.put("degraded", intentResult.isDegraded());
        response.put("secondaryIntents", intentResult.getSecondaryIntents());
        response.put("clarificationText", intentResult.getClarificationText());
        response.put("trace", trace);
        return response;
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(errorBody(message));
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return error;
    }
}
