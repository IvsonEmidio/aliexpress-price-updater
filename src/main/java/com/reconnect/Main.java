package com.reconnect;

import com.reconnect.domain.Product;
import com.reconnect.service.ProductService;
import com.reconnect.service.AliExpressPriceService;
import com.reconnect.service.SmsService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
public class Main {
    public static void main(String[] args) throws InterruptedException {
        ProductService productService = new ProductService();
        AliExpressPriceService aliexpressPriceService = new AliExpressPriceService();

        try {
            List<Product> products = productService.getAllProducts();
            
            for (Product product : products) {
                try {
                    String link = productService.getProductLink(product);
                    log.info("Processing product: {}", product);
                    
                    Optional<BigDecimal> price = Optional.empty();
                    int retries = 0;
                    int maxRetries = 3;
                    
                    while (price.isEmpty() && retries < maxRetries) {
                        try {
                            price = aliexpressPriceService.getPriceFromUrl(link);
                            if (price.isEmpty()) {
                                retries++;

                                if (retries < maxRetries) {
                                    log.info("Retry {} of {} for product {}", 
                                        retries, maxRetries, product.getId());
                                    Thread.sleep(5000);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error on attempt {} for product {}: {}", 
                                retries + 1, product.getId(), e.getMessage());
                            retries++;
                            if (retries < maxRetries) {
                                Thread.sleep(5000);
                            }
                        }
                    }
                    
                    price.ifPresentOrElse(
                        p -> {
                            BigDecimal priceInCents = p.multiply(new BigDecimal("100"));
                            long priceInCentsLong = priceInCents.longValue();
                            
                            log.info("AliExpress price for product {} in cents: {}", 
                                product.getId(), priceInCentsLong);
                                
                            productService.updateProductPrice(
                                product.getId(),
                                product.getLink(),
                                priceInCentsLong
                            );
                        },
                        () -> log.error("Failed to fetch price for product {} after {} attempts", 
                            product.getId(), maxRetries)
                    );
                    
                    Thread.sleep(5000);
                } catch (Throwable e) {
                    String failTextMessage = "DALE BURRO ELTON, O PROCESSO DE BUSCAR PRECOS FALHOU VISSE, BOM DAR UMA OLHADA";
                    SmsService.sendSms("5581988189893", failTextMessage);
                    SmsService.sendSms("5581997417562", failTextMessage);
                    log.error("Error processing product {}: {}", product.getId(), e.getMessage());
                }
            }
        } catch (Throwable e) {
            log.error("Error in main: {}", e.getMessage(), e);
        } finally {
            aliexpressPriceService.close();
        }
    }
}