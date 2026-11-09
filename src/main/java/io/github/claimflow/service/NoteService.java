package io.github.claimflow.service;

import io.github.claimflow.domain.AdjusterNote;
import io.github.claimflow.domain.AdjusterNoteRepository;
import io.github.claimflow.domain.Claim;
import io.github.claimflow.notes.AdjusterNoteSummarizer;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adjuster notes and their summaries. */
@Service
public class NoteService {

    private final ClaimService claimService;
    private final AdjusterNoteRepository notes;
    private final AdjusterNoteSummarizer summarizer;
    private final Clock clock;

    /** Creates the service. */
    public NoteService(ClaimService claimService, AdjusterNoteRepository notes, AdjusterNoteSummarizer summarizer,
                       Clock clock) {
        this.claimService = claimService;
        this.notes = notes;
        this.summarizer = summarizer;
        this.clock = clock;
    }

    /** Adds a note to a claim. */
    @Transactional
    public AdjusterNote add(String claimNumber, String author, String body) {
        Claim claim = claimService.load(claimNumber);
        return notes.save(new AdjusterNote(claim.getId(), author, body, clock.instant()));
    }

    /** Summarises all notes on a claim with the configured summariser. */
    @Transactional(readOnly = true)
    public AdjusterNoteSummarizer.NoteSummary summarize(String claimNumber) {
        Claim claim = claimService.load(claimNumber);
        return summarizer.summarize(notes.findByClaimIdOrderByIdAsc(claim.getId()));
    }
}
