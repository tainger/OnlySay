package com.onlysay;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 轻量 Web API 服务：暴露 REST 接口供 React 前端调试使用
 * 端点：
 *   POST /api/ingest   → 录入博主风格样本
 *   POST /api/generate → 根据用户输入生成同风格文案（含检索详情）
 *   GET  /api/health   → 健康检查
 */
public class ApiServer {

    private static IngestService ingestService;
    private static GenerateService generateService;
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

        // 生成文案
        app.post("/api/generate", ApiServer::generate);

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
            result.put("totalRecords", ingestService.getEmbeddingStore().size());
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
     * POST /api/generate - 根据用户输入生成同风格文案
     * Body: { "userInput": "今天去爬山了..." }
     */
    private static void generate(Context ctx) {
        try {
            // 解析请求体
            Map<String, String> body = mapper.readValue(ctx.body(), Map.class);
            String userInput = body.get("userInput");

            if (userInput == null || userInput.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "userInput 不能为空");
                ctx.json(error);
                return;
            }

            // 检查是否已录入样本
            if (ingestService.getEmbeddingStore().size() == 0) {
                ctx.status(HttpStatus.BAD_REQUEST);
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "请先录入样本（调用 /api/ingest）");
                ctx.json(error);
                return;
            }

            // 调用生成服务
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
}
