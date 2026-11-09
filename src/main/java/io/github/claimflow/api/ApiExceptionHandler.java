package io.github.claimflow.api;

import io.github.claimflow.notes.AdjusterNoteSummarizer;
import io.github.claimflow.service.NotFoundException;
import io.github.claimflow.service.StaleVersionException;
import io.github.claimflow.workflow.IllegalTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps domain exceptions to RFC 9457 problem responses; validation errors use Spring's defaults (400). */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(IllegalTransitionException.class)
    ProblemDetail illegalTransition(IllegalTransitionException e) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Transition not allowed", e.getMessage());
        pd.setProperty("fromStatus", e.getFrom());
        pd.setProperty("action", e.getAction());
        return pd;
    }

    @ExceptionHandler({StaleVersionException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Version conflict",
                e instanceof StaleVersionException ? e.getMessage() : "Claim was modified concurrently; reload and retry");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", e.getMessage());
    }

    @ExceptionHandler(AdjusterNoteSummarizer.SummarizationException.class)
    ProblemDetail summarizationFailed(AdjusterNoteSummarizer.SummarizationException e) {
        return problem(HttpStatus.BAD_GATEWAY, "Summarisation failed", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
