package nl.salah.civicsignal.reports;
import java.util.List;
public record DeadLetterPage(List<DeadLetterEvent> items, int page, int size, long totalElements, int totalPages) { }
