package com.payforge.account.repository;

import com.payforge.account.entity.Account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>{
}
