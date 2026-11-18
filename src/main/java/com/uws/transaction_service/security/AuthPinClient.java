package com.uws.transaction_service.security;

import com.uws.transaction_service.exception.UpiPinVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthPinClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${auth.service.url:http://localhost:4001}")
    private String authServiceUrl;

    public void verify(String userId, String pin) {
        try {
            restClientBuilder.baseUrl(authServiceUrl).build()
                    .post()
                    .uri("/auth/upi-pin/verify")
                    .header("X-User-Id", userId)
                    .body(Map.of("pin", pin))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new UpiPinVerificationException("Invalid or locked UPI PIN", HttpStatus.UNAUTHORIZED);
        } catch (RestClientException ex) {
            throw new UpiPinVerificationException("UPI PIN verification service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
