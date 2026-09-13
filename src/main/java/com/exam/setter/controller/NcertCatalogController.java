package com.exam.setter.controller;

import com.exam.setter.service.NcertCatalogService;
import com.exam.setter.service.NcertRetrievalService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ncert")
public class NcertCatalogController {
    private final NcertCatalogService catalogService;
    private final NcertRetrievalService retrievalService;

    public NcertCatalogController(NcertCatalogService catalogService, NcertRetrievalService retrievalService) {
        this.catalogService = catalogService;
        this.retrievalService = retrievalService;
    }

    @GetMapping("/catalog/books")
    public List<Map<String, Object>> books(@RequestParam(required = false) String subject,
                                           @RequestParam(required = false) String targetLevel,
                                           @RequestParam(required = false) String corpusVersion) {
        return catalogService.books(subject, targetLevel, corpusVersion);
    }

    @GetMapping("/catalog/chapters")
    public List<Map<String, Object>> chapters(@RequestParam String bookCode,
                                              @RequestParam(required = false) String targetLevel,
                                              @RequestParam(required = false) String corpusVersion) {
        return catalogService.chapters(bookCode, targetLevel, corpusVersion);
    }

    @GetMapping("/catalog/summary")
    public Map<String, Object> summary(@RequestParam(required = false) String corpusVersion) {
        return catalogService.summary(corpusVersion);
    }

    @GetMapping("/retrieval/diagnostic")
    public Map<String, Object> diagnostic(@RequestParam String subject,
                                          @RequestParam List<String> targetLevels,
                                          @RequestParam String query,
                                          @RequestParam(required = false) String corpusVersion,
                                          @RequestParam(required = false) String bookCode,
                                          @RequestParam(required = false) Integer chapterNumber,
                                          @RequestParam(defaultValue = "8") int topK) {
        List<Document> docs = retrievalService.retrieve(subject, targetLevels, query, corpusVersion, bookCode, chapterNumber, Math.min(20, Math.max(1, topK)));
        List<Map<String, Object>> results = docs.stream().map(d -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("score", d.getScore());
            result.put("citation", retrievalService.citation(d));
            result.put("metadata", d.getMetadata());
            result.put("text", d.getText());
            return result;
        }).toList();
        return Map.of("count", results.size(), "results", results);
    }
}
