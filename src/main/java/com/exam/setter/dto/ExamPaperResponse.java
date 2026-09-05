package com.exam.setter.dto;

import java.util.List;

public record ExamPaperResponse(
        List<GeneratedQuestion> questions
) {}