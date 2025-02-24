package com.reconnect.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reconnect.config.AppConfig;
import com.reconnect.model.Product;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class ProductService {
    private final HttpService httpService;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;
    private final LoggingService logger;

    public ProductService() {
        this.logger = new LoggingService(ProductService.class);
        this.httpService = new HttpService();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        this.apiBaseUrl = AppConfig.getInstance().getApiBaseUrl();
    }

    public List<Product> getAllProducts() {
        try {
            logger.startOperation("getAllProducts");
            logger.debug("Fetching all products from API");
            
            String response = httpService.get(
                apiBaseUrl + "/api/products",
                Map.of("Content-Type", "application/json")
            );
            
            List<Product> products = objectMapper.readValue(
                response, 
                new TypeReference<List<Product>>() {}
            );
            
            logger.info("Successfully fetched {} products", products.size());
            return products;
        } catch (Exception e) {
            logger.error("Error fetching products", e);
            throw new RuntimeException("Failed to fetch products", e);
        } finally {
            logger.endOperation("getAllProducts");
        }
    }

    public void updateProductPrice(String productId, String productLink, long priceInCents) {
        try {
            logger.startOperation("updateProductPrice");
            logger.debug("Updating price for product {} to {} cents", productId, priceInCents);
            
            var updateRequest = Map.of(
                "id", productId,
                "link", productLink,
                "price", priceInCents
            );
            
            String requestBody = objectMapper.writeValueAsString(updateRequest);
            String response = httpService.put(
                apiBaseUrl + "/api/products",
                requestBody,
                Map.of("Content-Type", "application/json")
            );
            
            logger.info("Successfully updated price for product {}", productId);
            logger.debug("Price update response: {}", response);
        } catch (Exception e) {
            logger.error("Error updating price for product " + productId, e);
            throw new RuntimeException("Failed to update product price", e);
        } finally {
            logger.endOperation("updateProductPrice");
        }
    }
} 