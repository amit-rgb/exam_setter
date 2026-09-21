package com.exam.setter.ncert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class NcertCorpusAdminService {
    private final NcertCorpusIngestionService ingestionService;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<JobState> state = new AtomicReference<>(new JobState("IDLE", null, null, null));

    public NcertCorpusAdminService(NcertCorpusIngestionService ingestionService,
                                   @Value("${app.ncert.admin-ingestion-enabled:false}") boolean enabled) {
        this.ingestionService = ingestionService;
        this.enabled = enabled;
    }

    public synchronized Map<String, Object> start() {
        if (!enabled) throw new IllegalStateException("Interactive NCERT ingestion is disabled. Use the dedicated NCERT batch profile or enable app.ncert.admin-ingestion-enabled explicitly.");
        if (!running.compareAndSet(false, true)) throw new IllegalStateException("An NCERT ingestion job is already running.");
        state.set(new JobState("RUNNING", Instant.now(), null, null));
        Thread.startVirtualThread(() -> {
            try {
                NcertCorpusIngestionService.RunSummary summary = ingestionService.ingestManifest();
                state.set(new JobState(summary.failed() == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS", state.get().startedAt(), Instant.now(), summary));
            } catch (Exception ex) {
                state.set(new JobState("FAILED", state.get().startedAt(), Instant.now(), ex.getMessage()));
            } finally {
                running.set(false);
            }
        });
        return status();
    }

    public Map<String, Object> status() {
        JobState current = state.get();
        return Map.of("enabled", enabled, "running", running.get(), "state", current.status(),
                "startedAt", current.startedAt() == null ? "" : current.startedAt().toString(),
                "finishedAt", current.finishedAt() == null ? "" : current.finishedAt().toString(),
                "result", current.result() == null ? "" : current.result());
    }

    private record JobState(String status, Instant startedAt, Instant finishedAt, Object result) {}
}
