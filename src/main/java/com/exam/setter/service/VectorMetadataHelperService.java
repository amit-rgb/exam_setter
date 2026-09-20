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
     * Returns distinct uploaded files for the requested subject/levels.
     * Supports current comma-separated targetLevels plus older JSON-array/scalar data.
     */
    public List<String> getDistinctFilesForSubjectAndLevels(String subject, List<String> targetLevels) {
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT (metadata->>'fileName') AS filename
            FROM document_embeddings
            WHERE metadata->>'fileName' IS NOT NULL
              AND COALESCE(metadata->>'source', 'USER_UPLOAD') = 'USER_UPLOAD'
              AND LOWER(metadata->>'subject') = ?
            """);
        List<Object> params = new java.util.ArrayList<>();
        params.add(subject.trim().toLowerCase());

        if (targetLevels != null && !targetLevels.isEmpty()) {
            sql.append("""
                AND EXISTS (
                    SELECT 1
                    FROM (
                        SELECT json_array_elements_text(metadata->'targetLevels') AS value
                        WHERE json_typeof(metadata->'targetLevels') = 'array'
                        UNION ALL
                        SELECT TRIM(value)
                        FROM unnest(string_to_array(
                            COALESCE(metadata->>'targetLevels', metadata->>'targetLevel', ''), ','
                        )) AS value
                        WHERE json_typeof(metadata->'targetLevels') <> 'array'
                           OR metadata->'targetLevels' IS NULL
                    ) levels
                    WHERE UPPER(levels.value) = ANY(?::text[])
                )
                """);
            params.add(targetLevels.stream().map(String::toUpperCase).toArray(String[]::new));
        }

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query(String.class)
                .list();
    }
}
