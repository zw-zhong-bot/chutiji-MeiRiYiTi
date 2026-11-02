package com.chutiji;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChutijiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChutijiApplication.class, args);
    }
}
