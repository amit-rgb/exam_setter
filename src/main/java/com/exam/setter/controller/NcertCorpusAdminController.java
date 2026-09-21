package com.exam.setter.controller;

import com.exam.setter.ncert.NcertCorpusAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/ncert/corpus")
public class NcertCorpusAdminController {
    private final NcertCorpusAdminService service;

    public NcertCorpusAdminController(NcertCorpusAdminService service) { this.service = service; }

    @GetMapping("/job")
    public Map<String, Object> jobStatus() { return service.status(); }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> start() { return ResponseEntity.accepted().body(service.start()); }
}
