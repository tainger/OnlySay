package com.onlysay;

import java.util.Scanner;

/**
 * OnlySay RAG MVP 主入口
 * CLI 交互菜单：录入样本 / 生成文案
 */
public class OnlySayApplication {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   OnlySay - RAG 风格生成 Demo");
        System.out.println("========================================");

        // 初始化服务
        IngestService ingestService = new IngestService();
        GenerateService generateService = new GenerateService(
                ingestService.getEmbeddingModel(),
                ingestService.getEmbeddingStore()
        );

        // 默认样本路径
        String samplesPath = "samples/blogger.md";

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;

            while (running) {
                System.out.println("\n----------------------------------------");
                System.out.println("请选择操作:");
                System.out.println("  1) 录入博主风格样本");
                System.out.println("  2) 输入你的事情，生成同风格文案");
                System.out.println("  3) 退出");
                System.out.print("请输入选项 (1/2/3): ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        ingestService.ingestSamples(samplesPath);
                        break;
                    case "2":
                        System.out.print("\n请输入你想分享的事情: ");
                        String userInput = scanner.nextLine().trim();
                        if (userInput.isEmpty()) {
                            System.out.println("⚠️ 输入不能为空");
                            break;
                        }
                        String result = generateService.generate(userInput);
                        System.out.println("========== 生成结果 ==========");
                        System.out.println(result);
                        System.out.println("==============================");
                        break;
                    case "3":
                        running = false;
                        System.out.println("👋 再见！");
                        break;
                    default:
                        System.out.println("⚠️ 无效选项，请重新输入");
                }
            }
        }
    }
}
