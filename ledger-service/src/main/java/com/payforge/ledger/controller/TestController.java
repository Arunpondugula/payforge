package com.payforge.ledger.controller;

import com.payforge.ledger.client.AccountClient;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

// Temporary — verifies the Feign wiring for Day 8.
// Replaced by the real POST /payment-intents endpoint on Day 10.
@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    private final AccountClient accountClient;

    public TestController(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    @GetMapping("/account/{id}")
    public AccountClient.AccountResponse callAccountService(@PathVariable UUID id) {
        return accountClient.getAccount(id);
    }
}