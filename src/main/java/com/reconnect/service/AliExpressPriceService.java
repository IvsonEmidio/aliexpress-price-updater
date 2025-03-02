package com.reconnect.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.reconnect.config.AppConfig;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.ReCaptcha;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;

@Slf4j
public class AliExpressPriceService {

    private final Playwright playwright;
    private final BrowserContext browser;
    private final LoggingService logger;
    private final TwoCaptcha solver;

    public AliExpressPriceService() {
        this.logger = new LoggingService(AliExpressPriceService.class);
        this.solver = new TwoCaptcha(AppConfig.getInstance().getCaptchaApiKey());
        playwright = Playwright.create();

        browser = playwright.chromium().launchPersistentContext(Path.of("./browser-data"),
                new BrowserType.LaunchPersistentContextOptions()
                        .setLocale("pt-BR")
                        .setHeadless(true)
                        .setTimezoneId("America/Sao_Paulo")
                        .setUserAgent(
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                        .setViewportSize(1920, 1080)
                        .setDeviceScaleFactor(1)
                        .setHasTouch(false)
                        .setJavaScriptEnabled(true)
                        .setIgnoreHTTPSErrors(true)
                        .setBypassCSP(true)
                        .setArgs(Arrays.asList(
                                "--disable-blink-features=AutomationControlled",
                                "--disable-web-security",
                                "--disable-features=IsolateOrigins,site-per-process"
                        ))
                        .setExtraHTTPHeaders(new HashMap<>() {
                            {
                                put("sec-ch-ua",
                                        "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"");
                                put("sec-ch-ua-platform", "\"Windows\"");
                                put("sec-ch-ua-mobile", "?0");
                                put("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7");
                                put("sec-fetch-site", "none");
                                put("sec-fetch-mode", "navigate");
                                put("sec-fetch-user", "?1");
                                put("sec-fetch-dest", "document");
                            }
                        }));
    }

    public Optional<BigDecimal> getPriceFromUrl(String url) {
        Page page = null;

        try {
            logger.startOperation("getPriceFromUrl");
            logger.info("Starting price fetch for URL: {}", url);

            page = browser.newPage();
            page.addInitScript("" +
                    "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
                    "Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });" +
                    "Object.defineProperty(navigator, 'languages', { get: () => ['pt-BR', 'pt', 'en-US', 'en'] });");

            logger.info("Navigating to URL: {}", url);
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(120000));

            simulateHumanBehavior(page);

            int maxCaptchaRetries = 3;
            int captchaAttempt = 0;
            boolean captchaSolved = false;

            while (captchaAttempt < maxCaptchaRetries && !captchaSolved) {
                if (isCaptchaPresent(page)) {
                    try {
                        handleCaptcha(page);

                        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(60000));
                        
                        Thread.sleep(5000);
                        captchaSolved = !isCaptchaPresent(page);
                        if (!captchaSolved) {
                            logger.info("Captcha still present after attempt {}. Retrying...", captchaAttempt + 1);
                        }
                    } catch (Exception e) {
                        logger.error("Captcha attempt {} failed: {}", captchaAttempt + 1, e.getMessage());
                    }
                    captchaAttempt++;
                } else {
                    captchaSolved = true;
                }
            }

            if (!captchaSolved) {
                logger.error("Failed to solve captcha after {} attempts", maxCaptchaRetries);
                return Optional.empty();
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

    private boolean isCaptchaPresent(Page page) {
        try {
            for (Frame frame : page.frames()) {
                if (frame.url().contains("acs.aliexpress.com")) {
                    String content = frame.content();
                    if (content.contains("We need to check if you are a robot.")) {
                        logger.info("Found robot verification text in frame");
                        return true;
                    }
                }
            }

            List<ElementHandle> captchaElements = page.querySelectorAll(
                    "[data-sitekey], " +
                            "#nocaptcha, " +
                            ".geetest_holder, " +
                            ".verify-wrap");

            return !captchaElements.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking for captcha presence: {}", e.getMessage());
            return false;
        }
    }

    private void handleCaptcha(Page page) {
        try {
            Frame captchaFrame = null;

            for (Frame frame : page.frames()) {
                frame.content();
                String frameUrl = frame.url();

                if (frameUrl.contains("acs.aliexpress.com") &&
                        frameUrl.contains("punish") &&
                        frameUrl.contains("recaptcha=1")) {
                    captchaFrame = frame;
                    logger.info("Found AliExpress captcha iframe");
                    break;
                }
            }

            if (captchaFrame != null) {
                Thread.sleep(30000);

                String content = captchaFrame.content();
                Pattern pattern = Pattern.compile("sitekey:\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(content);

                if (matcher.find()) {
                    String siteKey = matcher.group(1);
                    logger.info("Found reCAPTCHA site key: {}", siteKey);

                    ReCaptcha captcha = new ReCaptcha();
                    captcha.setSiteKey(siteKey);
                    captcha.setUrl(captchaFrame.url());
                    captcha.setInvisible(true);
                    captcha.setAction("verify");

                    try {
                        logger.info("Sending captcha to 2captcha service...");
                        solver.solve(captcha);
                        String response = captcha.getCode();
                        logger.info("Received captcha solution");

                        logger.info("Injecting captcha response into page...");
                        captchaFrame.addScriptTag(new Frame.AddScriptTagOptions()
                            .setContent("(function() {" +
                                "const response = '" + response + "';" +
                                "const textarea = document.getElementById('g-recaptcha-response');" +
                                "if (textarea) {" +
                                "    console.log(___grecaptcha_cfg.clients);" +
                                "    ___grecaptcha_cfg.clients[0].Z.Z.callback(response);" +
                                "}" +
                                "})();"));

                        Thread.sleep(30000);
                        logger.info("Captcha solved and submitted successfully");
                    } catch (Exception e) {
                        logger.error("2captcha error: {}", e.getMessage());
                        throw e;
                    }
                } else {
                    logger.error("Could not find reCAPTCHA site key in frame content");
                }
            } else {
                logger.error("Could not find AliExpress captcha iframe");
            }
        } catch (Exception e) {
            logger.error("Error handling captcha: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to handle captcha", e);
        }
    }

    private void simulateHumanBehavior(Page page) {
        try {
            page.evaluate("window.scrollTo(0, Math.floor(Math.random() * 100));");
            Thread.sleep(1000);

            page.mouse().move(100 + Math.random() * 100, 100 + Math.random() * 100);
            Thread.sleep(500);

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
                Thread.sleep(6000);
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

    public void close() throws InterruptedException {
        Thread.sleep(30);
        try {
            browser.close();
            playwright.close();
        } catch (Exception e) {
            logger.error("Error closing browser: {}", e.getMessage());
        }
    }
}