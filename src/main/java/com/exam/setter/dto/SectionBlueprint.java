package com.exam.setter.dto;

import com.exam.setter.model.QuestionType;

public record SectionBlueprint(
        String sectionName,           // e.g. "Section A - Foundations", "Section B - Numericals"
        QuestionType questionType,    // MCQ, NUMERICAL, etc.
        int questionCount,            // Kitne questions chahiye
        int marksPerQuestion,         // Har question ke marks
        double negativeMarks,         // e.g. 1.0 ya 0.0
        String difficulty             // "EASY", "MEDIUM", "HARD", "MIXED"
) {}