package nl.salah.civicsignal.workflow;

import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports/{reportId}")
public class CaseController {
    private final CaseService cases;
    public CaseController(CaseService cases) { this.cases = cases; }
    @GetMapping public CaseService.Detail detail(@PathVariable String reportId) { return cases.detail(reportId); }
    @PostMapping("/status") public WorkflowEvent status(@PathVariable String reportId, @RequestBody CaseService.StatusCommand command, Principal principal) {
        return cases.changeStatus(reportId, command, principal.getName());
    }
    @PostMapping("/notes") public WorkflowEvent note(@PathVariable String reportId, @RequestBody CaseService.NoteCommand command, Principal principal) {
        return cases.addNote(reportId, command, principal.getName());
    }
    @GetMapping("/audit") public CaseService.AuditPage audit(@PathVariable String reportId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return cases.audit(reportId, page, size);
    }
}
