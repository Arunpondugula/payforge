package com.payforge.ledger.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    // Deliberately a plain UUID, NOT a JPA relationship —
    // account-service owns Account in a completely separate database.
    // No foreign key across service boundaries is possible or correct here.
    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    protected LedgerEntry() {} // JPA

    public LedgerEntry(UUID accountId, EntryType entryType, BigDecimal amount) {
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
    }

    void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public UUID getId() { return id; }
    public Transaction getTransaction() { return transaction; }
    public UUID getAccountId() { return accountId; }
    public EntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}