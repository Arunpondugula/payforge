package com.payforge.ledger.exception;

public class UnbalancedTransactionException extends RuntimeException{
    public UnbalancedTransactionException(String message) {
        super(message);
    }
}
