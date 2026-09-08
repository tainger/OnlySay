package com.onlysay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OnlySay Web API 主入口（Spring Boot）。
 * 启动后监听 http://localhost:8080，提供 /api/* REST 端点供 React 前端调用。
 *
 * 启动命令：mvn spring-boot:run
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.onlysay.mapper")
public class OnlySayWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlySayWebApplication.class, args);
    }
}
