package com.reconnect.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.ReCaptcha;
import com.twocaptcha.exceptions.ApiException;
import com.twocaptcha.exceptions.NetworkException;
import com.twocaptcha.exceptions.ValidationException;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;

@Slf4j
public class AliExpressPriceService {

    private final Playwright playwright;
    private final BrowserContext browser;
    private final LoggingService logger;
    private final TwoCaptcha solver;
    private static final String CAPTCHA_API_KEY = "5e4f767365f9d5ef9f26ef77f616a9a1";

    public AliExpressPriceService() {
        this.logger = new LoggingService(AliExpressPriceService.class);
        this.solver = new TwoCaptcha(CAPTCHA_API_KEY);
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

    private void handleCaptcha(Page page) {
        try {
            ElementHandle recaptchaElement = page.querySelector("[data-sitekey]");
            if (recaptchaElement == null) {
                logger.debug("No reCAPTCHA element found");
                return;
            }

            String siteKey = recaptchaElement.getAttribute("data-sitekey");
            String pageUrl = page.url();

            logger.info("Found reCAPTCHA with site key: {}", siteKey);

            ReCaptcha captcha = new ReCaptcha();
            captcha.setSiteKey(siteKey);
            captcha.setUrl(pageUrl);
            captcha.setInvisible(true);
            captcha.setAction("verify");

            try {
                logger.info("Sending captcha to 2captcha service...");
                solver.solve(captcha);
                String response = captcha.getCode();
                logger.info("Received captcha solution");

                // Execute JavaScript to set the captcha response
                page.evaluate("(response) => {" +
                        "window.grecaptcha.enterprise.getResponse = () => response;" +
                        "document.querySelector('form').submit();" +
                        "}", response);

                // Wait for navigation after form submission
                Thread.sleep(5000);
                logger.info("Captcha solved and submitted successfully");
            } catch (ValidationException e) {
                logger.error("Invalid parameters passed: {}", e.getMessage());
            } catch (NetworkException e) {
                logger.error("Network error occurred: {}", e.getMessage());
            } catch (ApiException e) {
                logger.error("API error: {}", e.getMessage());
            } catch (TimeoutException e) {
                logger.error("Captcha solving timeout: {}", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error solving captcha: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to solve captcha", e);
        }
    }

    private boolean isCaptchaPresent(Page page) {
        try {
            ElementHandle captcha = page.querySelector("[data-sitekey], #nocaptcha, .geetest_holder");
            return captcha != null;
        } catch (Exception e) {
            return false;
        }
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

            logger.info("Navigating to URL: {}", url);
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, 
                new Page.WaitForLoadStateOptions().setTimeout(30000));

            simulateHumanBehavior(page);

            int maxAttempts = 3;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                if (isCaptchaPresent(page)) {
                    logger.info("Captcha detected, attempting to solve (attempt {}/{})", attempt + 1, maxAttempts);
                    handleCaptcha(page);
                    // Wait a bit after solving captcha
                    Thread.sleep(3000);
                } else {
                    break;
                }
            }

            return extractPriceWithRetry(page, 3);

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