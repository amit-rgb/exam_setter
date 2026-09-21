package com.exam.setter.controller;

import com.exam.setter.service.GenerationDiagnosticService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/diagnostics")
public class GenerationDiagnosticController {
    private final GenerationDiagnosticService diagnosticService;

    public GenerationDiagnosticController(GenerationDiagnosticService diagnosticService) {
        this.diagnosticService = diagnosticService;
    }

    @GetMapping("/generation")
    public Map<String, Object> latestGenerationDiagnostic() {
        return diagnosticService.latest();
    }
}
