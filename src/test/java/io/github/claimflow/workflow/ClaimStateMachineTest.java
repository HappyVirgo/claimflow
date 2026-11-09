package io.github.claimflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.claimflow.domain.ClaimStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClaimStateMachineTest {

    private final ClaimStateMachine sm = new ClaimStateMachine();
    private final TransitionContext none = TransitionContext.empty();

    @Test
    void fastTrackPathEndsClosed() {
        ClaimStatus s = sm.transition(ClaimStatus.SUBMITTED, ClaimAction.TRIAGE, none);
        s = sm.transition(s, ClaimAction.FAST_TRACK, new TransitionContext(true, null, null, null));
        assertThat(s).isEqualTo(ClaimStatus.FAST_TRACK_APPROVED);
        s = sm.transition(s, ClaimAction.PAY, none);
        assertThat(sm.transition(s, ClaimAction.CLOSE, none)).isEqualTo(ClaimStatus.CLOSED);
    }

    @Test
    void reviewPathWithInfoRequestThenApproval() {
        ClaimStatus s = sm.transition(ClaimStatus.TRIAGED, ClaimAction.ESCALATE, none);
        s = sm.transition(s, ClaimAction.REQUEST_INFO, new TransitionContext(false, "need invoice", null, null));
        assertThat(s).isEqualTo(ClaimStatus.NEEDS_INFO);
        s = sm.transition(s, ClaimAction.PROVIDE_INFO, none);
        TransitionContext approve = new TransitionContext(false, null, new BigDecimal("900"), new BigDecimal("1000"));
        assertThat(sm.transition(s, ClaimAction.APPROVE, approve)).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void fastTrackGuardRejectsIneligibleClaim() {
        assertThatThrownBy(() -> sm.transition(ClaimStatus.TRIAGED, ClaimAction.FAST_TRACK, none))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("Guard failed");
    }

    @Test
    void approveGuardEnforcesPositiveAmountWithinPayable() {
        BigDecimal payable = new BigDecimal("1000.00");
        assertThatThrownBy(() -> sm.transition(ClaimStatus.IN_REVIEW, ClaimAction.APPROVE,
                new TransitionContext(false, null, new BigDecimal("1000.01"), payable)))
                .isInstanceOf(IllegalTransitionException.class);
        assertThatThrownBy(() -> sm.transition(ClaimStatus.IN_REVIEW, ClaimAction.APPROVE,
                new TransitionContext(false, null, BigDecimal.ZERO, payable)))
                .isInstanceOf(IllegalTransitionException.class);
        assertThat(sm.transition(ClaimStatus.IN_REVIEW, ClaimAction.APPROVE,
                new TransitionContext(false, null, payable, payable))).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void denyRequiresReason() {
        assertThatThrownBy(() -> sm.transition(ClaimStatus.IN_REVIEW, ClaimAction.DENY,
                new TransitionContext(false, "  ", null, null)))
                .isInstanceOf(IllegalTransitionException.class);
        assertThat(sm.transition(ClaimStatus.IN_REVIEW, ClaimAction.DENY,
                new TransitionContext(false, "not covered", null, null))).isEqualTo(ClaimStatus.DENIED);
    }

    @Test
    void cannotSkipStates() {
        assertThatThrownBy(() -> sm.transition(ClaimStatus.SUBMITTED, ClaimAction.PAY, none))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> sm.transition(ClaimStatus.DENIED, ClaimAction.PAY, none))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(ClaimAction.class)
    void closedIsTerminal(ClaimAction action) {
        assertThat(sm.isTerminal(ClaimStatus.CLOSED)).isTrue();
        assertThatThrownBy(() -> sm.transition(ClaimStatus.CLOSED, action, none))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void everyNonClosedStateHasAnExit() {
        for (ClaimStatus s : ClaimStatus.values()) {
            assertThat(sm.isTerminal(s)).as(s.name()).isEqualTo(s == ClaimStatus.CLOSED);
        }
    }
}
