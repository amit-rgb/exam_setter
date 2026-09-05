package com.exam.setter.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorMetadataHelperService {

    private final JdbcClient jdbcClient;

    public VectorMetadataHelperService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Fetches distinct file names ingested for the given subject and target levels.
     */
    public List<String> getDistinctFilesForSubjectAndLevels(String subject, List<String> targetLevels) {
        String baseSql = """
            SELECT DISTINCT (metadata->>'fileName') as filename
            FROM document_embeddings
            WHERE LOWER(metadata->>'subject') = :subject
              AND metadata->>'fileName' IS NOT NULL
        """;

        if (targetLevels != null && !targetLevels.isEmpty()) {
            baseSql += " AND UPPER(metadata->>'targetLevel') IN (:levels)";
            return jdbcClient.sql(baseSql)
                    .param("subject", subject.trim().toLowerCase())
                    .param("levels", targetLevels.stream().map(String::toUpperCase).toList())
                    .query(String.class)
                    .list();
        }

        return jdbcClient.sql(baseSql)
                .param("subject", subject.trim().toLowerCase())
                .query(String.class)
                .list();
    }
}