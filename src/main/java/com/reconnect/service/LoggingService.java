package com.reconnect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

public class LoggingService {
    private final Logger logger;
    
    public LoggingService(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }
    
    public void startOperation(String operation) {
        MDC.put("operationId", UUID.randomUUID().toString());
        MDC.put("operation", operation);
        debug("Starting operation: {}", operation);
    }
    
    public void endOperation(String operation) {
        debug("Ending operation: {}", operation);
        MDC.clear();
    }
    
    public void debug(String message, Object... args) {
        logger.debug(message, args);
    }
    
    public void info(String message, Object... args) {
        logger.info(message, args);
    }
    
    public void error(String message, Object... args) {
        logger.error(message, args);
    }
    
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
} 