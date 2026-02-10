package com.nilsson.backend.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

@Repository
public class PinnedFolderRepository {

    private final JdbcClient jdbcClient;

    public PinnedFolderRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    public List<String> getPinnedFolders() {
        return jdbcClient.sql("SELECT path FROM pinned_folders ORDER BY path ASC")
                .query(String.class)
                .list();
    }

    public void addPinnedFolder(String path) {
        jdbcClient.sql("INSERT OR IGNORE INTO pinned_folders(path) VALUES(?)")
                .param(path)
                .update();
    }

    public void removePinnedFolder(String path) {
        jdbcClient.sql("DELETE FROM pinned_folders WHERE path = ?")
                .param(path)
                .update();
    }
}
