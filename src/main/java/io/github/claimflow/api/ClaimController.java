package io.github.claimflow.api;

import io.github.claimflow.domain.AdjusterNote;
import io.github.claimflow.domain.ClaimStatus;
import io.github.claimflow.notes.AdjusterNoteSummarizer;
import io.github.claimflow.service.ClaimService;
import io.github.claimflow.service.NoteService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for claims. */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claims;
    private final NoteService notes;

    /** Creates the controller. */
    public ClaimController(ClaimService claims, NoteService notes) {
        this.claims = claims;
        this.notes = notes;
    }

    /** FNOL intake: validates, persists and triages a new claim. */
    @PostMapping
    public ResponseEntity<ClaimResponse> submit(@Valid @RequestBody FnolRequest request) {
        ClaimResponse created = claims.submit(request);
        return ResponseEntity.created(URI.create("/api/claims/" + created.claimNumber())).body(created);
    }

    /** Looks up a claim by number. */
    @GetMapping("/{claimNumber}")
    public ClaimResponse get(@PathVariable String claimNumber) {
        return claims.get(claimNumber);
    }

    /** Searches claims by status or policy number. */
    @GetMapping
    public List<ClaimResponse> search(@RequestParam(required = false) ClaimStatus status,
                                      @RequestParam(required = false) String policyNumber) {
        return claims.search(status, policyNumber);
    }

    /** Applies an adjuster action (approve, deny, request info, pay, close...). */
    @PostMapping("/{claimNumber}/actions")
    public ClaimResponse act(@PathVariable String claimNumber, @Valid @RequestBody ActionRequest request) {
        return claims.act(claimNumber, request);
    }

    /** The append-only audit trail. */
    @GetMapping("/{claimNumber}/audit")
    public List<ClaimResponse.AuditView> audit(@PathVariable String claimNumber) {
        return claims.auditTrail(claimNumber);
    }

    /** Adds an adjuster note. */
    @PostMapping("/{claimNumber}/notes")
    public ResponseEntity<NoteView> addNote(@PathVariable String claimNumber, @Valid @RequestBody NoteRequest request) {
        AdjusterNote note = notes.add(claimNumber, request.author(), request.body());
        return ResponseEntity.status(201).body(new NoteView(note.getId(), note.getAuthor(), note.getBody(),
                note.getCreatedAt()));
    }

    /** Summarises the claim's adjuster notes. */
    @GetMapping("/{claimNumber}/notes/summary")
    public AdjusterNoteSummarizer.NoteSummary summary(@PathVariable String claimNumber) {
        return notes.summarize(claimNumber);
    }

    /** A stored note. */
    public record NoteView(Long id, String author, String body, Instant createdAt) {
    }
}
