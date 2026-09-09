package nl.salah.civicsignal.reports;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class ReportEventExceptionHandler {

    @ExceptionHandler(KafkaUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleKafkaUnavailable(KafkaUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("KAFKA_UNAVAILABLE", "Kafka is temporarily unavailable."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            InvalidReportQueryException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", "The request is invalid."));
    }

    @ExceptionHandler(ElasticsearchUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleElasticsearchUnavailable(ElasticsearchUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("ELASTICSEARCH_UNAVAILABLE", "Elasticsearch is temporarily unavailable."));
    }
}
