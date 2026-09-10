package nl.salah.civicsignal.reports;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

public final class ReportFilterQueryBuilder {

    private ReportFilterQueryBuilder() {
    }

    public static Query build(ReportFilterCriteria criteria) {
        BoolQuery.Builder query = new BoolQuery.Builder();
        boolean filtered = false;

        if (hasText(criteria.q())) {
            String text = criteria.q().trim();
            if (looksLikeReportId(text)) {
                query.must(q -> q.term(t -> t.field("reportId").value(text)));
            } else {
                query.must(q -> q.match(m -> m.field("searchableText").query(text)));
            }
            filtered = true;
        }
        filtered |= keyword(query, "sourceType", criteria.sourceType() == null ? null : criteria.sourceType().name());
        filtered |= keyword(query, "category", criteria.category());
        filtered |= keyword(query, "municipality", criteria.municipality());
        filtered |= keyword(query, "district", criteria.district());
        if ("NEW".equals(criteria.reportStatus())) {
            query.filter(newStatusQuery()); filtered = true;
        } else filtered |= keyword(query, "reportStatus", criteria.reportStatus());

        if (criteria.dateFrom() != null || criteria.dateTo() != null) {
            query.filter(q -> q.range(r -> r.date(d -> {
                d.field("occurredAt");
                if (criteria.dateFrom() != null) {
                    d.gte(criteria.dateFrom().toString());
                }
                if (criteria.dateTo() != null) {
                    d.lte(criteria.dateTo().toString());
                }
                return d;
            })));
            filtered = true;
        }
        return filtered ? Query.of(q -> q.bool(query.build())) : Query.of(q -> q.matchAll(m -> m));
    }

    private static boolean keyword(BoolQuery.Builder query, String field, String value) {
        if (!hasText(value)) {
            return false;
        }
        query.filter(q -> q.term(t -> t.field(field).value(value.trim())));
        return true;
    }

    static Query newStatusQuery() {
        return Query.of(q -> q.bool(b -> b.minimumShouldMatch("1")
                .should(s -> s.term(t -> t.field("reportStatus").value("NEW")))
                .should(s -> s.term(t -> t.field("reportStatus").value("")))
                .should(s -> s.bool(v -> v.mustNot(n -> n.exists(e -> e.field("reportStatus")))))));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean looksLikeReportId(String value) {
        return value.contains("-") && value.chars().anyMatch(Character::isDigit);
    }
}
