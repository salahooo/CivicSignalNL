package nl.salah.civicsignal.amsterdam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import nl.salah.civicsignal.amsterdam.sync.SourceSyncRepository;
import nl.salah.civicsignal.amsterdam.sync.SourceSyncRun;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/admin/sources/amsterdam")
public class AmsterdamController {
    private final AmsterdamProperties properties; private final AmsterdamAdapterService service; private final SourceSyncRepository syncs; private final AmsterdamImportScheduler scheduler;
    public AmsterdamController(AmsterdamProperties properties, AmsterdamAdapterService service, SourceSyncRepository syncs, AmsterdamImportScheduler scheduler) { this.properties=properties; this.service=service; this.syncs=syncs; this.scheduler=scheduler; }
    @GetMapping("/status") public Map<String,Object> status() { return Map.of("configured",true,"enabled",properties.enabled(),"sourceName","Gemeente Amsterdam Open Data","pageSize",properties.pageSize(),"maximumRecordsPerImport",properties.maximumRecordsPerImport(),"importRunning",service.running(),"apiKeyConfigured",properties.apiKey()!=null&&!properties.apiKey().isBlank(),"cursor",syncs.cursor(service.sourceName()).map(Object::toString).orElse("bootstrap pending"),"lastImportResult",service.last()==null?"none":service.last()); }
    @PostMapping("/import") public AmsterdamImportResult importNow(@RequestParam(defaultValue="true") boolean dryRun,@RequestParam(required=false) @Min(1) @Max(5) Integer limit, @RequestHeader(value="X-Amsterdam-Preview",required=false) String confirmationToken, java.security.Principal principal) { return service.manualImport(limit==null?Math.min(5,properties.maximumRecordsPerImport()):limit,dryRun,confirmationToken,principal.getName()); }
    @GetMapping("/runs") public Map<String,Object> runs(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { long total=syncs.countRuns(service.sourceName()); List<SourceSyncRun> items=syncs.runs(service.sourceName(),size,page*size); return Map.of("items",items,"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size); }
    @GetMapping("/scheduler") public AmsterdamSchedulerStatus schedulerStatus(){return scheduler.status();}
    @PostMapping("/scheduler/pause") public AmsterdamSchedulerStatus pause(){return scheduler.pause();}
    @PostMapping("/scheduler/resume") public AmsterdamSchedulerStatus resume(){return scheduler.resume();}
    @PostMapping("/scheduler/run-now") public AmsterdamSchedulerStatus runNow(){return scheduler.runNow();}
}
