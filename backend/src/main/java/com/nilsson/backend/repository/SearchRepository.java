package com.nilsson.backend.repository;

import com.nilsson.backend.service.FtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Repository for executing complex, high-performance image searches.
 * <p>
 * This class is the primary engine for the application's search functionality. It orchestrates
 * queries against both the standard relational {@code images} table and the specialized
 * {@code metadata_fts} virtual table (SQLite FTS5). It dynamically constructs SQL queries
 * that combine full-text search clauses with relational filters like star ratings.
 * <p>
 * Key functionalities:
 * - FTS5 Integration: Implements advanced full-text search with prefix matching and tokenization.
 * - Dynamic Query Building: Constructs complex {@code JOIN} and {@code WHERE} clauses based on active filters.
 * - Multi-Filter Support: Handles concurrent filtering by model, sampler, LoRA, and rating.
 * - Token Sanitization: Ensures search terms are correctly formatted to match the FTS5 index tokens.
 * - Pagination: Implements {@code LIMIT} and {@code OFFSET} for efficient frontend data loading.
 */
@Repository
public class SearchRepository {

    private static final Logger logger = LoggerFactory.getLogger(SearchRepository.class);
    private final JdbcClient jdbcClient;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]+");

    public SearchRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public List<String> findPaths(String query, Map<String, List<String>> filters, int offset, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT i.file_path FROM images i ");
        List<String> ftsClauses = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            String generalQuery = Arrays.stream(query.trim().split("\\s+"))
                    .map(s -> NON_ALPHANUMERIC.matcher(s).replaceAll("_") + "*")
                    .collect(Collectors.joining(" AND "));
            ftsClauses.add("(" + generalQuery + ")");
        }

        if (filters != null) {
            for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();

                if (values == null || values.isEmpty()) continue;

                List<String> validValues = values.stream()
                        .filter(v -> v != null && !v.isBlank() && !"All".equalsIgnoreCase(v))
                        .toList();

                if (validValues.isEmpty()) continue;

                if ("Rating".equals(key)) {
                    continue;
                }

                String fieldQuery;
                if ("Loras".equals(key)) {
                    fieldQuery = validValues.stream()
                            .map(v -> FtsService.formatFtsToken(key, v))
                            .collect(Collectors.joining(" OR "));
                } else {
                    fieldQuery = validValues.stream()
                            .map(v -> FtsService.formatFtsToken(key, v))
                            .collect(Collectors.joining(" OR "));
                }

                ftsClauses.add("(" + fieldQuery + ")");
            }
        }

        if (!ftsClauses.isEmpty()) {
            sql.append("JOIN metadata_fts fts ON i.id = fts.image_id WHERE fts.metadata_fts MATCH ? ");
            params.add(String.join(" AND ", ftsClauses));
        } else {
            sql.append("WHERE 1=1 ");
        }

        if (filters != null && filters.containsKey("Rating")) {
            List<String> ratingValues = filters.get("Rating").stream()
                    .filter(v -> v != null && !v.isBlank() && !"All".equalsIgnoreCase(v))
                    .toList();
            if (!ratingValues.isEmpty()) {
                handleRatingFilter(sql, params, ratingValues);
            }
        }


        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcClient.sql(sql.toString())
                .params(params)
                .query(String.class)
                .list();
    }

    private void handleRatingFilter(StringBuilder sql, List<Object> params, List<String> values) {
        List<String> conditions = new ArrayList<>();
        for (String val : values) {
            if ("Any Star Count".equalsIgnoreCase(val)) {
                conditions.add("i.rating > 0");
            } else {
                try {
                    Integer.parseInt(val);
                    conditions.add("i.rating = ?");
                    params.add(val);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid rating value skipped: {}", val);
                }
            }
        }
        if (!conditions.isEmpty()) {
            sql.append("AND (").append(String.join(" OR ", conditions)).append(") ");
        }
    }
}
