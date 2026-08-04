package com.payforge.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name ="account-service", url = "${account-service.url}")
public interface AccountClient {
    @GetMapping("/api/v1/accounts/{id}")
    AccountResponse getAccount(@PathVariable("id") UUID id);

    record AccountResponse(
            UUID id,
            String accountNumber,
            String firstName,
            String lastName,
            String contactEmail,
            BigDecimal balance,
            String currency,
            String status,
            String createdAt
    ) {}
}
