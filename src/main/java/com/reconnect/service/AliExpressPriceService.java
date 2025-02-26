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

    private boolean isCaptchaPresent(Page page) {
        try {
            // Check all frames recursively
            for (Frame frame : page.frames()) {
                try {
                    if (frame.url().contains("acs.aliexpress.com") && 
                        frame.url().contains("punish")) {
                        logger.info("Found AliExpress captcha iframe");
                        return true;
                    }
                } catch (Exception e) {
                    logger.debug("Error checking iframe: {}", e.getMessage());
                }
            }
            return false;
        } catch (Exception e) {
            logger.debug("Error checking for captcha: {}", e.getMessage());
            return false;
        }
    }

    private void handleCaptcha(Page page) {
        try {
            // Find the outer punish iframe
            Frame outerFrame = page.frames().stream()
                .filter(f -> f.url().contains("acs.aliexpress.com") && 
                           f.url().contains("punish"))
                .findFirst()
                .orElse(null);

            if (outerFrame == null) {
                logger.info("No captcha iframe found");
                return;
            }

            // Find the inner iframe that contains the actual reCAPTCHA
            Frame innerFrame = outerFrame.childFrames().stream()
                .filter(f -> f.url().contains("recaptcha=1"))
                .findFirst()
                .orElse(null);

            if (innerFrame == null) {
                logger.info("No reCAPTCHA iframe found");
                return;
            }

            ElementHandle recaptchaElement = innerFrame.querySelector("[data-sitekey]");
            if (recaptchaElement == null) {
                logger.info("No reCAPTCHA element found in iframe");
                return;
            }

            String siteKey = recaptchaElement.getAttribute("data-sitekey");
            String pageUrl = innerFrame.url();

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

                // Execute JavaScript in the correct iframe context
                innerFrame.evaluate("(response) => {" +
                    "window.grecaptcha.enterprise.getResponse = () => response;" +
                    "document.querySelector('form').submit();" +
                    "}", response);

                Thread.sleep(5000);
                logger.info("Captcha solved and submitted successfully");
            } catch (ValidationException | NetworkException | ApiException | TimeoutException e) {
                logger.error("Captcha solving error: {}", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error solving captcha: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to solve captcha", e);
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