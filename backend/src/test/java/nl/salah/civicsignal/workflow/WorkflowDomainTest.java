package nl.salah.civicsignal.workflow;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

class WorkflowDomainTest {
    static Stream<Arguments> transitions() {
        Set<String> valid=Set.of("NEW:TRIAGED","NEW:REJECTED","TRIAGED:IN_PROGRESS","TRIAGED:REJECTED","TRIAGED:NEW",
                "IN_PROGRESS:RESOLVED","IN_PROGRESS:TRIAGED","IN_PROGRESS:REJECTED","RESOLVED:CLOSED","RESOLVED:IN_PROGRESS","CLOSED:IN_PROGRESS","REJECTED:TRIAGED");
        return Arrays.stream(CaseStatus.values()).flatMap(from -> Arrays.stream(CaseStatus.values()).map(to -> Arguments.of(from,to,valid.contains(from+":"+to))));
    }
    @ParameterizedTest @MethodSource("transitions")
    void everyTransition(CaseStatus from, CaseStatus to, boolean allowed) { assertThat(from.next().contains(to)).isEqualTo(allowed); }
    @Test void normalizesAndBoundsText() {
        assertThat(WorkflowInput.text("  veilig\r\n tekst\u202e ",500,true)).isEqualTo("veilig tekst");
        assertThatThrownBy(() -> WorkflowInput.text("x".repeat(2001),2000,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowInput.text("\n ",2000,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowInput.reportId("id\nforged")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void typedContractsRejectUnknownTypeAndVersionAndProjectionExcludesInternalData() throws Exception {
        var json=JsonMapper.builder().findAndAddModules().build();
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        var state=new WorkflowState(CaseStatus.NEW,1,now,now,null,null,0,List.of());
        var note=new NoteAdded(UUID.randomUUID(),1,"REPORT_NOTE_ADDED","TEST-1",now,"admin","safe-id",UUID.randomUUID(),"private demo text",now,state);
        String encoded=json.writeValueAsString(note);
        WorkflowEvent decoded=json.readValue(encoded,WorkflowEvent.class);
        decoded.validate(); assertThat(decoded).isEqualTo(note);
        assertThatThrownBy(() -> json.readValue(encoded.replace("REPORT_NOTE_ADDED","UNKNOWN"),WorkflowEvent.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        var unsupported=json.readValue(encoded.replace("\"schemaVersion\":1","\"schemaVersion\":2"),WorkflowEvent.class);
        assertThatThrownBy(unsupported::validate).isInstanceOf(IllegalArgumentException.class);
        String projection=json.writeValueAsString(WorkflowProjection.publicFields(state));
        assertThat(projection).doesNotContain("private demo text","actor","reason","noteId");
        assertThat(WorkflowProjection.publicFields(state)).containsEntry("reportStatus","NEW").containsEntry("workflowVersion",1L);
    }
}
