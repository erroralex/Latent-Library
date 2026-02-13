package com.nilsson.backend.repository;

import com.nilsson.backend.exception.ValidationException;
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
 * Repository for executing complex, multi-criteria searches across the image library.
 * <p>
 * This class serves as the primary engine for the application's search functionality. It dynamically
 * constructs SQL queries that combine standard relational filtering (e.g., ratings, collection membership)
 * with high-performance full-text search (FTS) using SQLite's FTS5 extension.
 * <p>
 * <b>Security Note:</b> This class implements strict input sanitization to prevent SQL injection and
 * FTS query syntax manipulation. All user-provided text is stripped of non-alphanumeric characters
 * before being passed as a bind parameter to the {@code MATCH} operator.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Dynamic Query Construction:</b> Builds complex SQL statements on-the-fly based on a combination
 *   of text queries, metadata filters (Model, Sampler, etc.), and collection constraints.</li>
 *   <li><b>FTS Integration:</b> Leverages the {@code metadata_fts} virtual table to perform rapid
 *   text-based lookups across all indexed image metadata and prompts.</li>
 *   <li><b>Filter Orchestration:</b> Manages the intersection of multiple filter types, ensuring that
 *   rating filters, collection boundaries, and search terms are applied correctly in a single atomic operation.</li>
 *   <li><b>Pagination Support:</b> Implements efficient {@code LIMIT} and {@code OFFSET} logic to support
 *   infinite scrolling and large result sets in the UI.</li>
 * </ul>
 */
@Repository
public class SearchRepository {

    private static final Logger logger = LoggerFactory.getLogger(SearchRepository.class);
    private final JdbcClient jdbcClient;

    // Strict whitelist: Only allow letters and numbers. All other chars (including SQL control chars) are removed.
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]+");

    public SearchRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public List<String> findPaths(String query, Map<String, List<String>> filters, int offset, int limit) {
        return findPaths(query, filters, null, offset, limit);
    }

    public List<String> findPaths(String query, Map<String, List<String>> filters, List<String> collectionPaths, int offset, int limit) {
        if (offset < 0) throw new ValidationException("Offset cannot be negative.");
        if (limit <= 0) throw new ValidationException("Limit must be greater than zero.");

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT i.file_path FROM images i ");
        List<String> ftsClauses = new ArrayList<>();

        // 1. Handle Free Text Search
        if (query != null && !query.isBlank()) {
            // Sanitize: "DROP TABLE" -> "DROP TABLE" (safe text), ";--" -> " "
            String generalQuery = Arrays.stream(query.trim().split("\\s+"))
                    .map(s -> NON_ALPHANUMERIC.matcher(s).replaceAll(" ") + "*")
                    .collect(Collectors.joining(" AND "));
            ftsClauses.add("(" + generalQuery + ")");
        }

        // 2. Handle Metadata Filters (Model, Sampler, etc.)
        if (filters != null) {
            for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) continue;

                List<String> validValues = values.stream()
                        .filter(v -> v != null && !v.isBlank() && !"All".equalsIgnoreCase(v))
                        .toList();
                if (validValues.isEmpty()) continue;
                if ("Rating".equals(key)) continue; // Handled separately

                // FtsService.formatFtsToken also applies strict sanitization
                String fieldQuery = validValues.stream()
                        .map(v -> FtsService.formatFtsToken(key, v))
                        .collect(Collectors.joining(" OR "));
                ftsClauses.add("(" + fieldQuery + ")");
            }
        }

        // 3. Construct FTS Clause
        if (!ftsClauses.isEmpty()) {
            // We use a parameterized query for the MATCH expression.
            // The 'ftsClauses' string is passed as a single bind parameter.
            // Because we sanitized the inputs above, this string is safe FTS syntax.
            sql.append("JOIN metadata_fts fts ON i.id = fts.image_id WHERE fts.global_text MATCH ? ");
            params.add(String.join(" AND ", ftsClauses));
        } else {
            sql.append("WHERE 1=1 ");
        }

        // 4. Handle Rating Filter (Relational)
        if (filters != null && filters.containsKey("Rating")) {
            List<String> ratingValues = filters.get("Rating").stream()
                    .filter(v -> v != null && !v.isBlank() && !"All".equalsIgnoreCase(v))
                    .toList();
            if (!ratingValues.isEmpty()) {
                handleRatingFilter(sql, params, ratingValues);
            }
        }

        // 5. Handle Collection Constraints (Relational)
        if (collectionPaths != null) {
            if (collectionPaths.isEmpty()) return new ArrayList<>();
            sql.append(" AND i.file_path IN (");
            for (int i = 0; i < collectionPaths.size(); i++) {
                sql.append("?");
                if (i < collectionPaths.size() - 1) sql.append(",");
                params.add(collectionPaths.get(i));
            }
            sql.append(") ");
        }

        // 6. Pagination
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
