package com.reconnect.service;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class HttpService {
    private final HttpClient httpClient;

    public HttpService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String get(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            log.debug("GET request to {} returned status code: {}", url, response.statusCode());
            return response.body();
        } catch (Exception e) {
            log.error("Error executing GET request to {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to execute GET request", e);
        }
    }

    public String post(String url, String body, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            log.debug("POST request to {} returned status code: {}", url, response.statusCode());
            return response.body();
        } catch (Exception e) {
            log.error("Error executing POST request to {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to execute POST request", e);
        }
    }

    public String put(String url, String body, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            log.debug("PUT request to {} returned status code: {}", url, response.statusCode());
            return response.body();
        } catch (Exception e) {
            log.error("Error executing PUT request to {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to execute PUT request", e);
        }
    }
} 