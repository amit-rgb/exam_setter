package com.exam.setter.controller;

import com.exam.setter.service.IngestionDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class IngestionDashboardController {

    private final IngestionDashboardService dashboardService;

    public IngestionDashboardController(IngestionDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/ingested-documents")
    public Map<String, Object> ingestedDocuments() {
        return dashboardService.getDashboard();
    }
}
