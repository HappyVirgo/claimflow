package io.github.claimflow.notes;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the summariser: Claude when {@code ANTHROPIC_API_KEY} is set, otherwise the offline one. */
@Configuration
public class SummarizerConfig {

    /** The active summariser bean. */
    @Bean
    public AdjusterNoteSummarizer adjusterNoteSummarizer(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        if (apiKey.isBlank()) {
            return new ExtractiveNoteSummarizer();
        }
        return new ClaudeNoteSummarizer(AnthropicOkHttpClient.builder().apiKey(apiKey).build());
    }
}
