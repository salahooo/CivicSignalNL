package nl.salah.civicsignal.amsterdam;

import jakarta.servlet.http.HttpServletRequest;
import nl.salah.civicsignal.observability.RequestIds;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Order(-1)
@RestControllerAdvice(assignableTypes = AmsterdamController.class)
public class AmsterdamExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception error, HttpServletRequest request) {
        HttpStatusCode status = HttpStatus.SERVICE_UNAVAILABLE;
        String code = "AMSTERDAM_INFRASTRUCTURE", detail = "De importvoorziening is tijdelijk niet beschikbaar.", external = "none";
        if (error instanceof AmsterdamFailure failure) {
            status = failure.getStatusCode(); code = failure.code(); detail = failure.getReason(); external = failure.externalStatusClass();
        } else if (error instanceof jakarta.validation.ConstraintViolationException
                || error instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || error instanceof org.springframework.web.bind.MissingServletRequestParameterException) {
            status = HttpStatus.BAD_REQUEST; code = "AMSTERDAM_INVALID_REQUEST"; detail = "De importaanvraag is ongeldig.";
        } else if (error instanceof ResponseStatusException failure) {
            status = failure.getStatusCode(); code = "AMSTERDAM_REQUEST_FAILED"; detail = "De importaanvraag kon niet worden uitgevoerd.";
        }
        String requestId = RequestIds.safe(request.getHeader(RequestIds.HEADER));
        String mdc = org.slf4j.MDC.get(RequestIds.MDC_KEY);
        if (mdc != null) requestId = RequestIds.safe(mdc);
        LoggerFactory.getLogger(AmsterdamExceptionHandler.class).warn("Amsterdam requestId={} category={} externalStatusClass={}", requestId, code, external);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("Amsterdam-import"); problem.setProperty("code", code); problem.setProperty("requestId", requestId);
        return ResponseEntity.status(status).header(RequestIds.HEADER, requestId).body(problem);
    }
}
