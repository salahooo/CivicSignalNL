package nl.salah.civicsignal.reports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.util.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.junit.jupiter.api.Test;

class WorkflowAnalyticsTest {
    @Test void mapsWorkflowAggregationsAndResolutionRateExactly() {
        Map<String,Aggregate> a=new HashMap<>();
        for(String key:List.of("open","closed","withLocation","overdueOpen","reopenedReports")) a.put(key,Aggregate.of(v->v.filter(f->f.docCount(1))));
        a.put("observedCases",Aggregate.of(v->v.filter(f->f.docCount(4))));
        a.put("workflowResolved",Aggregate.of(v->v.filter(f->f.docCount(3))));
        for(String key:List.of("averageResolution","workflowResolutionAvg","workflowClosureAvg")) a.put(key,Aggregate.of(v->v.avg(f->f.value(2.5))));
        for(String key:List.of("p50Resolution","workflowResolutionP50","workflowClosureP50")) a.put(key,Aggregate.of(v->v.tdigestPercentiles(f->f.values(p->p.keyed(Map.of("50.0","2.0"))))));
        a.put("earliest",Aggregate.of(v->v.min(f->f.value(0)))); a.put("latest",Aggregate.of(v->v.max(f->f.value(0))));
        for(String key:List.of("topCategories","topSources","topMunicipalities","topDistricts","topStatuses")) a.put(key,Aggregate.of(v->v.sterms(f->f.buckets(b->b.array(List.of())).sumOtherDocCount(0L).docCountErrorUpperBound(0L))));
        a.put("timeline",Aggregate.of(v->v.dateHistogram(f->f.buckets(b->b.array(List.of())))));
        a.put("statusChanges",Aggregate.of(v->v.sum(f->f.value(7))));
        SearchResponse<Void> response=SearchResponse.of(r->r.took(1).timedOut(false).shards(s->s.total(1).successful(1).failed(0))
                .hits(h->h.hits(List.of()).total(t->t.value(4).relation(co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq))).aggregations(a));
        var result=new AnalyticsService(mock(ElasticsearchClient.class)).response(response,AnalyticsInterval.DAY).workflow();
        assertThat(result.resolutionRate()).isEqualTo(.75);
        assertThat(result.statusChanges()).isEqualTo(7);
        assertThat(result.averageNewToResolvedDays()).isEqualTo(2.5);
        assertThat(result.p50NewToResolvedDays()).isEqualTo(2);
        assertThat(result.averageNewToClosedDays()).isEqualTo(2.5);
        assertThat(result.p50NewToClosedDays()).isEqualTo(2);
        assertThat(result.overdueOpen()).isEqualTo(1); assertThat(result.reopenedReports()).isEqualTo(1);
    }
}
