package com.skillbook;

import com.skillbook.auth.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class SkillBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillBookApplication.class, args);
    }
}