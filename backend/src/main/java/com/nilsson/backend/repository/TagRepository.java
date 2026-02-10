package com.nilsson.backend.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;

@Repository
public class TagRepository {

    private final JdbcClient jdbcClient;

    public TagRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public void addTag(int imageId, String tag) {
        jdbcClient.sql("INSERT OR IGNORE INTO image_tags(image_id, tag) VALUES(?, ?)")
                .param(imageId)
                .param(tag)
                .update();
    }

    public void removeTag(int imageId, String tag) {
        jdbcClient.sql("DELETE FROM image_tags WHERE image_id = ? AND tag = ?")
                .param(imageId)
                .param(tag)
                .update();
    }

    public Set<String> getTags(int imageId) {
        return new HashSet<>(jdbcClient.sql("SELECT tag FROM image_tags WHERE image_id = ?")
                .param(imageId)
                .query(String.class)
                .list());
    }
}
