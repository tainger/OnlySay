package com.onlysay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlysay.intent.IntentRecognizer;
import com.onlysay.intent.IntentResult;
import com.onlysay.intent.IntentType;
import com.onlysay.intent.IntentTrace;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 轻量 Web API 服务：暴露 REST 接口供 React 前端调试使用
 * 端点：
 *   POST /api/ingest        → 录入博主风格样本
 *   POST /api/generate      → 前置意图路由：创作/改写走 RAG 生成，其余返回澄清/占位
 *   POST /api/intent        → 纯意图识别调试（不触发下游执行）
 *   GET  /api/intent/stats  → 识别指标聚合（各层命中率/兜底率/澄清率/P95/P99）
 *   GET  /api/health        → 健康检查
 */
public class ApiServer {

    private static IngestService ingestService;
    private static GenerateService generateService;
    private static IntentRecognizer intentRecognizer;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        // 初始化服务（首次会下载 ONNX 模型）
        System.out.println("========================================");
        System.out.println("  OnlySay Web API 启动中...");
        System.out.println("========================================");

        try {
            ingestService = new IngestService();
            generateService = new GenerateService(
                    ingestService.getEmbeddingModel(),
                    ingestService.getEmbeddingStore()
            );
            intentRecognizer = new IntentRecognizer(new com.onlysay.intent.CorrectionRecorder());
            System.out.println("🧭 意图识别漏斗已就绪（enabled=" + Config.isIntentEnabled()
                    + ", classifier=" + Config.getIntentClassifier() + "）");
        } catch (Exception e) {
            System.err.println("❌ 服务初始化失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        int port = 8080;
        Javalin app = Javalin.create(config -> {
            // 配置 CORS，允许前端跨域访问
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> {
                it.anyHost();
                it.allowCredentials = false;
            }));
        }).start(port);

        System.out.println("\n🚀 Web API 已启动: http://localhost:" + port);
        System.out.println("   前端开发服务器请运行在其他端口（如 5173）");
        System.out.println("========================================\n");

        // 健康检查
        app.get("/api/health", ApiServer::health);

        // 录入样本
        app.post("/api/ingest", ApiServer::ingest);

        // 生成文案（前置意图路由）
        app.post("/api/generate", ApiServer::generate);

        // 纯意图识别调试
        app.post("/api/intent", ApiServer::intent);

        // 识别指标
        app.get("/api/intent/stats", ApiServer::intentStats);

        // 优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n👋 正在关闭服务...");
            app.stop();
        }));
    }

    /**
     * GET /api/health - 健康检查
     */
    private static void health(Context ctx) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "OnlySay RAG API 运行中");
        ctx.json(result);
    }

    /**
     * POST /api/ingest - 录入博主风格样本
     * Body: {} (无参数，使用默认样本路径)
     */
    private static void ingest(Context ctx) {
        try {
            String samplesPath = "samples/blogger.md";
            ingestService.ingestSamples(samplesPath);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "样本录入完成");
            result.put("totalRecords", ingestService.getRecordCount());
            ctx.json(result);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "录入失败: " + e.getMessage());
            ctx.json(error);
        }
    }

    /**
     * POST /api/generate - 前置意图路由 + 同风格文案生成
     * Body: { "userInput": "今天去爬山了...", "sessionId": "可选" }
     * 路由：CONTENT_GENERATION/REWRITE → RAG；CLARIFICATION → 反问；其他意图 → 占位
     */
    private static void generate(Context ctx) {
        try {
            // 解析请求体
            Map<String, String> body = mapper.readValue(ctx.body(), Map.class);
            String userInput = body.get("userInput");
            String sessionId = body.get("sessionId");

            if (userInput == null || userInput.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "userInput 不能为空");
                ctx.json(error);
                return;
            }

            // ===== 前置意图路由（intent.enabled=false 时跳过，行为与旧版一致）=====
            if (Config.isIntentEnabled()) {
                IntentRecognizer.Recognition recognition = intentRecognizer.recognize(userInput, sessionId);
                IntentResult intentResult = recognition.result();
                IntentType intent = intentResult.getIntent();

                // 澄清反问：不进入生成
                if (intent == IntentType.CLARIFICATION) {
                    Map<String, Object> response = baseResponse(intentResult, recognition.trace());
                    response.put("success", true);
                    response.put("clarification", true);
                    response.put("message", intentResult.getClarificationText());
                    ctx.json(response);
                    return;
                }

                // 已识别但暂不支持的业务意图：占位响应
                if (intent != IntentType.CONTENT_GENERATION && intent != IntentType.REWRITE) {
                    ctx.status(HttpStatus.NOT_IMPLEMENTED);
                    Map<String, Object> response = baseResponse(intentResult, recognition.trace());
                    response.put("success", false);
                    response.put("message", "已识别意图「" + intent + "」，但该能力暂不支持，敬请期待");
                    ctx.json(response);
                    return;
                }

                // 创作意图：先查样本库，再走 RAG（响应附带 intent/trace）
                if (ingestService.getRecordCount() == 0) {
                    ctx.status(HttpStatus.BAD_REQUEST);
                    Map<String, Object> error = baseResponse(intentResult, recognition.trace());
                    error.put("success", false);
                    error.put("message", "请先录入样本（调用 /api/ingest）");
                    ctx.json(error);
                    return;
                }

                GenerateService.GenerateResult result = generateService.generateWithDetails(userInput);
                Map<String, Object> response = baseResponse(intentResult, recognition.trace());
                response.put("success", true);
                response.put("generatedText", result.getGeneratedText());
                response.put("retrievedSamples", result.getRetrievedSamples());
                ctx.json(response);
                return;
            }

            // ===== 旧版直通路径（intent.enabled=false）=====
            if (ingestService.getRecordCount() == 0) {
                ctx.status(HttpStatus.BAD_REQUEST);
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "请先录入样本（调用 /api/ingest）");
                ctx.json(error);
                return;
            }

            GenerateService.GenerateResult result = generateService.generateWithDetails(userInput);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("generatedText", result.getGeneratedText());
            response.put("retrievedSamples", result.getRetrievedSamples());
            ctx.json(response);

        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "生成失败: " + e.getMessage());
            ctx.json(error);
        }
    }

    /**
     * POST /api/intent - 纯意图识别调试（不触发任何下游业务执行）
     * Body: { "userInput": "...", "sessionId": "可选" }
     */
    private static void intent(Context ctx) {
        if (!Config.isIntentEnabled()) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "意图识别模块已关闭（intent.enabled=false）");
            ctx.json(error);
            return;
        }
        try {
            Map<String, String> body = mapper.readValue(ctx.body(), Map.class);
            String userInput = body.get("userInput");
            String sessionId = body.get("sessionId");

            if (userInput == null || userInput.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "userInput 不能为空");
                ctx.json(error);
                return;
            }

            IntentRecognizer.Recognition recognition = intentRecognizer.recognize(userInput, sessionId);
            Map<String, Object> response = baseResponse(recognition.result(), recognition.trace());
            response.put("success", true);
            ctx.json(response);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "意图识别失败: " + e.getMessage());
            ctx.json(error);
        }
    }

    /**
     * GET /api/intent/stats - 识别指标聚合
     */
    private static void intentStats(Context ctx) {
        ctx.json(intentRecognizer.getMetrics().snapshot());
    }

    /** 公共响应字段：intent/slots/confidence/hitLayer/secondaryIntents/clarificationText/trace */
    private static Map<String, Object> baseResponse(IntentResult intentResult, IntentTrace trace) {
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
}
