package com.flycat.rm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class RmApplication {
    public static void main(String[] args) {
        SpringApplication.run(RmApplication.class, args);
    }
}
