package io.github.claimflow.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for adjuster notes. */
public interface AdjusterNoteRepository extends JpaRepository<AdjusterNote, Long> {

    /** Notes for a claim, oldest first. */
    List<AdjusterNote> findByClaimIdOrderByIdAsc(Long claimId);
}
