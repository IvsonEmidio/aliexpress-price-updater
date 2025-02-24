package com.reconnect;

import com.reconnect.model.Product;
import com.reconnect.service.ProductService;
import com.reconnect.service.AliExpressPriceService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
public class Main {
    public static void main(String[] args) {
        ProductService productService = new ProductService();
        AliExpressPriceService priceService = new AliExpressPriceService();
        Random random = new Random();
        
        try {
            List<Product> products = productService.getAllProducts();
            
            for (Product product : products) {
                try {
                    String link = product.getLink() + """
                            ?pdp_ext_f=%7B"sku_id":"
                            """ + product.getSkuId() + """
                                    "%7D
                                    """;

                    log.info("Processing product: {}", product);
                    Optional<BigDecimal> price = priceService.getPriceFromUrl(link);
                    
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
                        () -> log.error("Failed to fetch price for product {}", product.getId())
                    );
                    
                    // Random delay between 1 and 7 seconds
                    int delay = random.nextInt(6000) + 1000;
                    log.debug("Waiting for {} milliseconds before next request", delay);
                    Thread.sleep(delay);
                } catch (Exception e) {
                    log.error("Error processing product {}: {}", product.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in main: {}", e.getMessage(), e);
        } finally {
            priceService.close();
        }
    }
}