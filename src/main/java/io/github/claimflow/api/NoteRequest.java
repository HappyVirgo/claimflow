package io.github.claimflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new adjuster note. */
public record NoteRequest(@NotBlank @Size(max = 100) String author, @NotBlank @Size(max = 4000) String body) {
}
