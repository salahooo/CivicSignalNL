package nl.salah.civicsignal.reports;

import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;

public enum AnalyticsInterval {
    DAY(CalendarInterval.Day),
    WEEK(CalendarInterval.Week),
    MONTH(CalendarInterval.Month);

    private final CalendarInterval elasticsearchInterval;

    AnalyticsInterval(CalendarInterval elasticsearchInterval) {
        this.elasticsearchInterval = elasticsearchInterval;
    }

    CalendarInterval elasticsearchInterval() {
        return elasticsearchInterval;
    }
}
