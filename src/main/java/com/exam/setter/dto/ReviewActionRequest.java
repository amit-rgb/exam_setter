package com.exam.setter.dto;

import java.util.List;
import java.util.UUID;

public record ReviewActionRequest(
        UUID questionId,
        String reviewerId,        // e.g. "prof_amit"
        boolean approved,         // true = APPROVE, false = REJECT
        String comments,          // Optional feedback/remarks
        String editedQuestionText,// Optional: User can fix LaTeX formula if needed
        List<String> editedOptions // Optional: Option corrections
) {}