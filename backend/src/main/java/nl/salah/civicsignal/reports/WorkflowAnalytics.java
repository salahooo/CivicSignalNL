package nl.salah.civicsignal.reports;

public record WorkflowAnalytics(long observedCases, Double averageNewToResolvedDays, Double p50NewToResolvedDays,
                                Double averageNewToClosedDays, Double p50NewToClosedDays,
                                long overdueOpen, int overdueDays, long statusChanges, Double resolutionRate, long reopenedReports) { }
