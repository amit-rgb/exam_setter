package com.exam.setter.dto;

import java.util.List;

public record SourceIngestionResult(
        String status,
        String fileName,
        String subject,
        List<String> targetLevels,
        String sourceType,
        int indexedChunks
) {}
