package com.codepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodePulseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodePulseApplication.class, args);
    }
}
