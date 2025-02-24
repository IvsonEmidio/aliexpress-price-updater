package com.reconnect.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;

@Slf4j
public class AliExpressPriceService {

    private final Playwright playwright;
    private final BrowserContext browser;
    private final LoggingService logger;

    public AliExpressPriceService() {
        this.logger = new LoggingService(AliExpressPriceService.class);
        playwright = Playwright.create();
        // Enhanced stealth settings
        browser = playwright.chromium().launchPersistentContext(Path.of("./browser-data"), 
                new BrowserType.LaunchPersistentContextOptions()
                    .setLocale("pt-BR")
                    .setHeadless(true)
                    .setTimezoneId("America/Sao_Paulo")
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
                    .setDeviceScaleFactor(1)
                    .setHasTouch(false)
                    .setJavaScriptEnabled(true)
                    .setIgnoreHTTPSErrors(true)
                    .setBypassCSP(true)
                    .setArgs(Arrays.asList("--disable-blink-features=AutomationControlled"))
                    .setExtraHTTPHeaders(new HashMap<>() {{
                        put("sec-ch-ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"");
                        put("sec-ch-ua-platform", "\"Windows\"");
                        put("sec-ch-ua-mobile", "?0");
                        put("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7");
                        put("sec-fetch-site", "none");
                        put("sec-fetch-mode", "navigate");
                        put("sec-fetch-user", "?1");
                        put("sec-fetch-dest", "document");
                    }}));
    }

    public Optional<BigDecimal> getPriceFromUrl(String url) {
        Page page = null;
        try {
            logger.startOperation("getPriceFromUrl");
            logger.info("Starting price fetch for URL: {}", url);
            
            page = browser.newPage();

            // Add random mouse movements and scrolling
            page.addInitScript("" +
                "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
                "Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });" +
                "Object.defineProperty(navigator, 'languages', { get: () => ['pt-BR', 'pt', 'en-US', 'en'] });"
            );

            // Navigate to the URL
            logger.info("Navigating to URL: {}", url);
            page.navigate(url);


            // Initial wait for page load
            logger.info("Waiting for page to load completely...");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(30000));
            logger.debug("DOM content loaded");

          
            simulateHumanBehavior(page);

            // Check for captcha and handle it
            if (isCaptchaPresent(page)) {
                logger.info("Captcha detected, waiting for manual resolution...");
                waitForCaptchaResolution(page);
            }

            // Try different price selectors with retry mechanism
          
            Optional<BigDecimal> price = extractPriceWithRetry(page, 3);
            price.ifPresentOrElse(
                p -> logger.info("Successfully extracted price: {}", p),
                () -> logger.error("Failed to extract price from page")
            );
            
            return price;
        } catch (Exception e) {
            logger.error("Error fetching price from AliExpress", e);
            return Optional.empty();
        } finally {
            if (page != null) {
                try {
                    page.close();
                    logger.debug("Closed browser tab for URL: {}", url);
                } catch (Exception e) {
                    logger.error("Error closing browser tab", e);
                }
            }
            logger.endOperation("getPriceFromUrl");
        }
    }

    private void simulateHumanBehavior(Page page) {
        try {
            // Random scroll
            page.evaluate("window.scrollTo(0, Math.floor(Math.random() * 100));");
            Thread.sleep(1000);
            
            // Move mouse randomly
            page.mouse().move(100 + Math.random() * 100, 100 + Math.random() * 100);
            Thread.sleep(500);
            
            // More natural scroll
            page.evaluate("window.scrollTo(0, document.body.scrollHeight / 2);");
            Thread.sleep(1000);
        } catch (Exception e) {
            logger.debug("Error during human behavior simulation: {}", e.getMessage());
        }
    }

    private boolean isCaptchaPresent(Page page) {
        try {
            ElementHandle captcha = page.querySelector("#nocaptcha");
            return captcha != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForCaptchaResolution(Page page) {
        try {
            // Wait for price element to appear (indicating captcha is resolved)
            page.waitForSelector("span.product-price-value", new Page.WaitForSelectorOptions().setTimeout(60000));
        } catch (Exception e) {
            logger.error("Timeout waiting for captcha resolution");
        }
    }

    private Optional<BigDecimal> extractPriceWithRetry(Page page, int maxRetries) {
        String[] priceSelectors = {
                "span.product-price-value",
                ".uniform-banner-box-price",
                "[class*='Price_uniformBannerBoxPrice']",
                "[class*='Price_promotion']"
        };

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Thread.sleep(2000); // Wait between attempts

                for (String selector : priceSelectors) {
                    ElementHandle element = page.querySelector(selector);
                    if (element != null) {
                        String priceText = element.textContent();
                        logger.info("Found price with selector {}: {}", selector, priceText);
                        
                        Pattern pattern = Pattern.compile("\\d+[.,]\\d+");
                        Matcher matcher = pattern.matcher(priceText);

                        if (matcher.find()) {
                            String price = matcher.group().replace(",", ".");
                            return Optional.of(new BigDecimal(price));
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        logger.info("Price not found after {} attempts", maxRetries);
        return Optional.empty();
    }

    public void close() {
        try {
            browser.close();
            playwright.close();
        } catch (Exception e) {
            logger.error("Error closing browser: {}", e.getMessage());
        }
    }
}