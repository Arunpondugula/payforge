package com.payforge.account.dto;

import com.payforge.account.entity.Account;
import com.payforge.account.entity.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        String firstName,
        String lastName,
        String contactEmail,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getFirstName(),
                account.getLastName(),
                account.getContactEmail(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}