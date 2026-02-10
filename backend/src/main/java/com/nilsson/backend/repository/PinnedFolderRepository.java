package com.nilsson.backend.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * Repository for managing user-pinned directories.
 * <p>
 * This class provides persistent storage for file system paths that the user has "bookmarked"
 * for quick access. It manages the {@code pinned_folders} table, ensuring that paths are
 * stored uniquely and can be retrieved in alphabetical order.
 * <p>
 * Key functionalities:
 * - Bookmark Persistence: Saves absolute file system paths to the database.
 * - Duplicate Prevention: Uses {@code INSERT OR IGNORE} to handle redundant pin requests.
 * - Retrieval: Returns a sorted list of all pinned directory paths.
 * - Removal: Deletes specific path entries from the pinned list.
 */
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
