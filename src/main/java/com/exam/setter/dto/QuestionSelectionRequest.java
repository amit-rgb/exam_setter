package com.exam.setter.dto;

import java.util.List;
import java.util.UUID;

public record QuestionSelectionRequest(List<UUID> includedQuestionIds) {}
