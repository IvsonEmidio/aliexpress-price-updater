package com.reconnect.config;

import lombok.Getter;
import java.io.IOException;
import java.util.Properties;

@Getter
public class AppConfig {
    private static AppConfig instance;
    private final String apiBaseUrl;

    private AppConfig() {
        Properties props = new Properties();
        try {
            props.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
        this.apiBaseUrl = props.getProperty("api.base.url", "http://localhost:8080").trim();
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }
} 