package io.github.claimflow.notes;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import io.github.claimflow.domain.AdjusterNote;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Summariser backed by Claude via the official Anthropic Java SDK, using structured outputs.
 * Only wired when {@code ANTHROPIC_API_KEY} is set; tests, CI and benchmarks use the offline one.
 */
public class ClaudeNoteSummarizer implements AdjusterNoteSummarizer {

    static final String MODEL = "claude-opus-5-5";
    private static final String SYSTEM = "You summarise insurance adjuster notes for a claims supervisor. "
            + "Be factual, keep amounts and dates exactly as written, and do not speculate.";

    /** Structured output shape requested from the model. */
    public static class Payload {
        /** Two or three sentence summary. */
        public String summary;
        /** Up to five key facts. */
        public List<String> keyPoints;
    }

    private final AnthropicClient client;

    /** Creates the summariser around a configured SDK client. */
    public ClaudeNoteSummarizer(AnthropicClient client) {
        this.client = client;
    }

    @Override
    public NoteSummary summarize(List<AdjusterNote> notes) {
        String transcript = notes.stream()
                .map(n -> "[" + n.getCreatedAt() + "] " + n.getAuthor() + ": " + n.getBody())
                .collect(Collectors.joining("\n"));
        StructuredMessageCreateParams<Payload> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16_000)
                .system(SYSTEM)
                .addUserMessage("Summarise these adjuster notes:\n" + transcript)
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .outputConfig(StructuredOutputConfig.<Payload>builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                        .format(Payload.class, JsonSchemaLocalValidation.YES)
                        .build())
                .build();
        try {
            StructuredMessage<Payload> message = client.messages().create(params);
            if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                throw new SummarizationException("Claude declined to summarise these notes (stop_reason=refusal)", null);
            }
            Payload payload = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new SummarizationException("Claude returned no text content", null));
            List<String> points = payload.keyPoints == null ? List.of() : List.copyOf(payload.keyPoints);
            return new NoteSummary(payload.summary, points, "claude");
        } catch (RateLimitException e) {
            throw new SummarizationException("Anthropic rate limit hit; retry later", e);
        } catch (AnthropicServiceException e) {
            throw new SummarizationException("Anthropic API error (HTTP " + e.statusCode() + ")", e);
        } catch (AnthropicIoException e) {
            throw new SummarizationException("Could not reach the Anthropic API", e);
        }
    }
}
