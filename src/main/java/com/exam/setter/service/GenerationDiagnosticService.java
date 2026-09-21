package com.exam.setter.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GenerationDiagnosticService {
    private final AtomicReference<Map<String, Object>> latest = new AtomicReference<>(
            Map.of("status", "NO_ATTEMPT", "timestamp", Instant.now().toString()));

    public void recordSuccess(Map<String, Object> details) {
        Map<String, Object> result = new LinkedHashMap<>(details);
        result.put("status", "SUCCESS");
        result.put("timestamp", Instant.now().toString());
        latest.set(result);
    }

    public void recordFailure(Map<String, Object> details, Throwable error) {
        Map<String, Object> result = new LinkedHashMap<>(details);
        result.put("status", "FAILED");
        result.put("timestamp", Instant.now().toString());
        result.put("errorType", error == null ? "" : error.getClass().getName());
        result.put("error", error == null ? "" : String.valueOf(error.getMessage()));
        latest.set(result);
    }

    public Map<String, Object> latest() {
        return latest.get();
    }
}
