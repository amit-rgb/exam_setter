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
     *
     * New vectors store targetLevels as a JSON array. Legacy vectors may only have
     * the scalar targetLevel field, so both representations are matched.
     */
    public List<String> getDistinctFilesForSubjectAndLevels(String subject, List<String> targetLevels) {
        String baseSql = """
            SELECT DISTINCT (metadata->>'fileName') as filename
            FROM document_embeddings
            WHERE LOWER(metadata->>'subject') = :subject
              AND metadata->>'fileName' IS NOT NULL
        """;

        if (targetLevels != null && !targetLevels.isEmpty()) {
            baseSql += """
                AND EXISTS (
                    SELECT 1
                    FROM json_array_elements_text(
                        CASE
                            WHEN json_typeof(metadata->'targetLevels') = 'array'
                                THEN metadata->'targetLevels'
                            ELSE json_build_array(metadata->>'targetLevel')
                        END
                    ) AS level(value)
                    WHERE UPPER(level.value) IN (:levels)
                )
                """;
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