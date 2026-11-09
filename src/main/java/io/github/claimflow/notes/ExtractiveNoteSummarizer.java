package io.github.claimflow.notes;

import io.github.claimflow.domain.AdjusterNote;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Offline, deterministic extractive summariser: splits notes into sentences, scores each by
 * claims-relevant keywords, presence of amounts/dates and recency, and returns the top sentences in
 * their original order. No network access.
 */
public class ExtractiveNoteSummarizer implements AdjusterNoteSummarizer {

    private static final int MAX_SENTENCES = 3;
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern AMOUNT_OR_DATE = Pattern.compile("\\$?\\d[\\d,]*(\\.\\d+)?|\\d{4}-\\d{2}-\\d{2}");
    private static final Map<String, Double> KEYWORDS = Map.ofEntries(
            Map.entry("estimate", 2.0), Map.entry("invoice", 2.0), Map.entry("police", 2.0),
            Map.entry("approve", 2.5), Map.entry("deny", 2.5), Map.entry("denied", 2.5),
            Map.entry("inconsistent", 3.0), Map.entry("fraud", 3.0), Map.entry("injury", 2.5),
            Map.entry("requested", 1.5), Map.entry("confirmed", 1.5), Map.entry("photos", 1.0),
            Map.entry("liability", 2.0), Map.entry("deductible", 1.5), Map.entry("total loss", 3.0),
            Map.entry("witness", 1.5), Map.entry("recorded statement", 2.0));

    private record Candidate(int order, String text, double score) {
    }

    @Override
    public NoteSummary summarize(List<AdjusterNote> notes) {
        if (notes.isEmpty()) {
            return new NoteSummary("No adjuster notes yet.", List.of(), "offline-extractive");
        }
        List<Candidate> candidates = new ArrayList<>();
        int order = 0;
        for (int n = 0; n < notes.size(); n++) {
            double recency = 1.0 + (double) n / notes.size();
            for (String sentence : SENTENCE_SPLIT.split(notes.get(n).getBody().trim())) {
                if (!sentence.isBlank()) {
                    candidates.add(new Candidate(order++, sentence.trim(), score(sentence) * recency));
                }
            }
        }
        List<String> top = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed().thenComparingInt(Candidate::order))
                .limit(MAX_SENTENCES)
                .sorted(Comparator.comparingInt(Candidate::order))
                .map(Candidate::text)
                .toList();
        return new NoteSummary(String.join(" ", top), top, "offline-extractive");
    }

    static double score(String sentence) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        double s = 0.1;
        for (Map.Entry<String, Double> kw : KEYWORDS.entrySet()) {
            if (lower.contains(kw.getKey())) {
                s += kw.getValue();
            }
        }
        if (AMOUNT_OR_DATE.matcher(sentence).find()) {
            s += 1.0;
        }
        return s;
    }
}
