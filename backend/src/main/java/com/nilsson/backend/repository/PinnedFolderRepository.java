package com.nilsson.backend.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * Repository for managing user-pinned directories for rapid file system navigation.
 * <p>
 * This class provides persistent storage for absolute file system paths that the user has
 * "bookmarked" or pinned within the application's file explorer. It manages the
 * {@code pinned_folders} table, ensuring that paths are stored uniquely and can be
 * retrieved in a consistent, sorted order for display in the UI.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Bookmark Persistence:</b> Saves absolute file system paths to the database,
 *   allowing user navigation state to survive application restarts.</li>
 *   <li><b>Duplicate Prevention:</b> Utilizes {@code INSERT OR IGNORE} to gracefully handle
 *   redundant pin requests for the same directory path.</li>
 *   <li><b>Retrieval:</b> Returns a sorted list of all pinned directory paths, optimized
 *   for rendering in navigation menus or tree views.</li>
 *   <li><b>Removal:</b> Deletes specific path entries from the pinned list when a user
 *   chooses to unpin a directory.</li>
 * </ul>
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
