package com.skillbook;

import com.skillbook.auth.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
public class SkillBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillBookApplication.class, args);
    }
}