package io.github.claimflow.notes;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.claimflow.domain.AdjusterNote;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractiveNoteSummarizerTest {

    private final ExtractiveNoteSummarizer summarizer = new ExtractiveNoteSummarizer();

    private static AdjusterNote note(String body) {
        return new AdjusterNote(1L, "adj.test", body, Instant.parse("2026-06-01T10:00:00Z"));
    }

    @Test
    void picksInformativeSentencesInOriginalOrder() {
        var summary = summarizer.summarize(List.of(
                note("Called the claimant. Weather was nice. Repair estimate of $4,200 received from the shop."),
                note("Police report confirms the other driver ran the light. Spoke briefly about scheduling."),
                note("Photos are inconsistent with the described rear impact.")));
        assertThat(summary.provider()).isEqualTo("offline-extractive");
        assertThat(summary.keyPoints()).containsExactly(
                "Repair estimate of $4,200 received from the shop.",
                "Police report confirms the other driver ran the light.",
                "Photos are inconsistent with the described rear impact.");
    }

    @Test
    void emptyNotesYieldPlaceholder() {
        var summary = summarizer.summarize(List.of());
        assertThat(summary.keyPoints()).isEmpty();
        assertThat(summary.summary()).contains("No adjuster notes");
    }

    @Test
    void isDeterministic() {
        List<AdjusterNote> notes = List.of(note("A. B. C. D. E."));
        assertThat(summarizer.summarize(notes)).isEqualTo(summarizer.summarize(notes));
        assertThat(summarizer.summarize(notes).keyPoints()).containsExactly("A.", "B.", "C.");
    }
}
