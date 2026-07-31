package com.payforge.account.controller;

import com.payforge.account.dto.AccountResponse;
import com.payforge.account.dto.CreateAccountRequest;
import com.payforge.account.entity.Account;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.payforge.account.service.AccountService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService){
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request){
        Account account = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse>getAccount(@PathVariable UUID id){
        Account account = accountService.getAccount(id);
        return ResponseEntity.ok(AccountResponse.from(account));
    }

}
