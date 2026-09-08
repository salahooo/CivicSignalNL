package nl.salah.civicsignal.reports;

import java.time.Duration;
import java.time.Instant;

public record GeneratorStatus(boolean enabledByConfiguration, boolean running, int generatedThisRun,
                              int maximumPerRun, Duration interval, double duplicateProbability,
                              double invalidEventProbability, Instant lastGeneratedAt) { }
