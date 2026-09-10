package nl.salah.civicsignal.workflow;

import java.text.Normalizer;

public final class WorkflowInput {
    private WorkflowInput() { }
    public static String text(String value, int max, boolean required) {
        if (value == null) { if (required) throw new IllegalArgumentException(); return null; }
        if (value.length() > max) throw new IllegalArgumentException();
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        if (normalized.length() > max || required && normalized.isBlank()) throw new IllegalArgumentException();
        return normalized.isEmpty() ? null : normalized;
    }
    public static String reportId(String value) {
        if (value == null || value.isBlank() || value.length() > 200 || java.util.regex.Pattern.compile("[\\p{Cc}\\p{Cf}]").matcher(value).find() || value.contains("/"))
            throw new IllegalArgumentException();
        return value;
    }
}
