package io.github.claimflow.notes;

import io.github.claimflow.domain.AdjusterNote;
import java.util.List;

/** Produces a short summary of a claim's adjuster notes. */
public interface AdjusterNoteSummarizer {

    /**
     * Summarises the notes (oldest first).
     *
     * @throws SummarizationException if the provider cannot produce a summary
     */
    NoteSummary summarize(List<AdjusterNote> notes);

    /**
     * A summary and the sentences or points it is built from.
     *
     * @param provider which implementation produced it ("offline-extractive" or "claude")
     */
    record NoteSummary(String summary, List<String> keyPoints, String provider) {
    }

    /** Raised when summarisation fails (e.g. an LLM refusal or API error). */
    class SummarizationException extends RuntimeException {
        /** Creates the exception. */
        public SummarizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
