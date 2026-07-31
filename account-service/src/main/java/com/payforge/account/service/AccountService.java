package com.payforge.account.service;

import com.payforge.account.dto.CreateAccountRequest;
import com.payforge.account.entity.Account;
import com.payforge.account.exception.AccountNotFoundException;
import com.payforge.account.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

    public Account createAccount(CreateAccountRequest request){
        Account account = new Account(
                request.firstName(),
                request.lastName(),
                request.contactEmail(),
                request.initialBalance(),
                request.currency()

        );
        return accountRepository.save(account);
    }

    public Account getAccount(UUID id){
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
