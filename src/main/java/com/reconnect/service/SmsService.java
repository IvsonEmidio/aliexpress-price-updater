package com.reconnect.service;

import com.reconnect.config.AppConfig;
import com.vonage.client.VonageClient;
import com.vonage.client.sms.messages.TextMessage;

public class SmsService {
    public static void sendSms(String phoneNumber, String text) {
        String vonageApiKey = AppConfig.getInstance().getVonageApiKey();
        String vonageApiSecret = AppConfig.getInstance().getVonageApiSecret();
        VonageClient client = VonageClient.builder().apiKey(vonageApiKey).apiSecret(vonageApiSecret).build();

        TextMessage message = new TextMessage("PriceUpdater",
                phoneNumber,
                text
        );

        client.getSmsClient().submitMessage(message);
    }
}
