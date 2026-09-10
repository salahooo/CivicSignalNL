package nl.salah.civicsignal.workflow;

import nl.salah.civicsignal.observability.RequestIds;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(-10)
@RestControllerAdvice(assignableTypes = {CaseController.class, OutboxController.class})
public class WorkflowProblems {
    @ExceptionHandler(WorkflowFailure.class) ProblemDetail workflow(WorkflowFailure error) { return problem(error.status(), error.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception error) { return problem(400, "Controleer de invoer en verplichte versie."); }
    @ExceptionHandler(DataIntegrityViolationException.class) ProblemDetail conflict(Exception error) { return problem(409, "Conflicterende opdracht. Laad het dossier opnieuw."); }
    @ExceptionHandler(DataAccessException.class) ProblemDetail unavailable(Exception error) { return problem(503, "Workflowopslag tijdelijk niet beschikbaar."); }
    private ProblemDetail problem(int status, String message) {
        var detail = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.valueOf(status), message);
        detail.setProperty("requestId", RequestIds.safe(MDC.get(RequestIds.MDC_KEY)));
        return detail;
    }
}
