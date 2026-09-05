package com.exam.setter.dto;

import java.util.List;

public record ExamPaperBlueprintRequest(
        String examTitle,             // e.g. "NEET Diagnostic Test - Chemistry"
        String subject,               // "chemistry"
        List<String> targetLevels,    // ["CLASS_11"], ["CLASS_12"] ya null
        int durationMinutes,          // e.g. 60
        List<SectionBlueprint> sections
) {}