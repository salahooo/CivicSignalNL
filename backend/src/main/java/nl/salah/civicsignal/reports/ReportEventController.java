package nl.salah.civicsignal.reports;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/report-events")
public class ReportEventController {

    private final ReportEventProducer reportEventProducer;

    public ReportEventController(ReportEventProducer reportEventProducer) {
        this.reportEventProducer = reportEventProducer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReportEvent publish(@Valid @RequestBody ReportEventRequest request) {
        return reportEventProducer.publish(request);
    }
}
