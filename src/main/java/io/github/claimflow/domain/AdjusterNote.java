package io.github.claimflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Free-text note written by an adjuster while working a claim. */
@Entity
@Table(name = "adjuster_note")
public class AdjusterNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdjusterNote() {
    }

    /** Creates a note. */
    public AdjusterNote(Long claimId, String author, String body, Instant createdAt) {
        this.claimId = claimId;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
