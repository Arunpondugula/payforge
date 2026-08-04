package com.payforge.ledger.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column
    private TransactionStatus status;
    @Column (nullable = false, unique = true)
    private String idempotencyKey;
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerEntry> entries = new ArrayList<>();
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = TransactionStatus.PENDING;
    }
    protected Transaction() {}
    public Transaction(String idempotencyKey){
        this.idempotencyKey = idempotencyKey;
    }
    public void addEntry(LedgerEntry entry) {
        entries.add(entry);
        entry.setTransaction(this);
    }
    public void markCompleted() { this.status = TransactionStatus.COMPLETED; }
    public void markFailed() { this.status = TransactionStatus.FAILED; }

    public UUID getId() { return id; }
    public TransactionStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public List<LedgerEntry> getEntries() { return entries; }
    public Instant getCreatedAt() { return createdAt; }

}
