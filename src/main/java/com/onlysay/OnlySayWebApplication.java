package com.onlysay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OnlySay Web API 主入口（Spring Boot）。
 * 启动后监听 http://localhost:8080，提供 /api/* REST 端点供 React 前端调用。
 *
 * 启动命令：mvn spring-boot:run
 * CLI 模式：mvn spring-boot:run -Dspring-boot.run.profiles=cli
 */
@SpringBootApplication
@MapperScan("com.onlysay.mapper")
public class OnlySayWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlySayWebApplication.class, args);
    }
}
