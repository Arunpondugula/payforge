package com.payforge.ledger.service;

import com.payforge.ledger.entity.Transaction;
import com.payforge.ledger.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        transaction.assertBalanced();
        return transactionRepository.save(transaction);
    }
}