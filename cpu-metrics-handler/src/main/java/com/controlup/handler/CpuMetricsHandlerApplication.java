package com.controlup.handler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
// @EnableRetry - Temporarily disabled due to missing AspectJ dependency
public class CpuMetricsHandlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CpuMetricsHandlerApplication.class, args);
    }
}
