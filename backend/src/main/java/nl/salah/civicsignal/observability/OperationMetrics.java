package nl.salah.civicsignal.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class OperationMetrics {
    private final MeterRegistry meters;
    public OperationMetrics(MeterRegistry meters) { this.meters = meters; }
    @Around("execution(public * nl.salah.civicsignal.reports.ReportDocumentIndexer.index(..)) || "
            + "execution(public * nl.salah.civicsignal.reports.ReportSearchService.search(..)) || "
            + "execution(public * nl.salah.civicsignal.reports.AnalyticsService.summary(..)) || "
            + "execution(public * nl.salah.civicsignal.reports.ReportMapService.map(..)) || "
            + "execution(public * nl.salah.civicsignal.reports.ReportEventProducer.publishEvent(..)) || "
            + "execution(public * nl.salah.civicsignal.reports.ReportEventProducer.publish(..)) || "
            + "execution(public * nl.salah.civicsignal.amsterdam.AmsterdamAdapterService.importRecords(..))")
    public Object measure(ProceedingJoinPoint call) throws Throwable {
        var sample = Timer.start(meters);
        String outcome = "success";
        try (var ignored = RequestIds.scope(org.slf4j.MDC.get(RequestIds.MDC_KEY))) { return call.proceed(); }
        catch (Throwable error) { outcome = "failure"; throw error; }
        finally {
            sample.stop(meters.timer("civic.operations", "operation", call.getSignature().getName(), "outcome", outcome));
        }
    }
}
