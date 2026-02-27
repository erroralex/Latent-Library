package com.nilsson.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Listener that facilitates the dynamic port and security handshake between the Spring Boot backend and Electron frontend.
 * <p>
 * This component listens for the {@link WebServerInitializedEvent}, which is fired once the embedded
 * servlet container has successfully started and bound to a port. It retrieves the actual port
 * (which may be dynamic if configured with port 0) and the unique security handshake token
 * from {@link SecurityConfig}.
 * <p>
 * Key Responsibilities:
 * <ul>
 * <li><b>Handshake Orchestration:</b> Writes the port and security token to a shared file
 * ({@code data/port.txt}) in a standardized {@code port:token} format.</li>
 * <li><b>Filesystem Integration:</b> Ensures the target directory exists and utilizes
 * atomic file moves to prevent race conditions during Electron's aggressive startup polling.</li>
 * <li><b>Lifecycle Synchronization:</b> Acts as the final step in the backend startup
 * sequence, signaling to the Electron process that the server is ready for traffic.</li>
 * </ul>
 */
@Component
public class PortFileWriter implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(PortFileWriter.class);
    private static final String PORT_FILE = "data/port.txt";
    private static final String TEMP_PORT_FILE = "data/port.txt.tmp";

    private final String appDataDir;

    public PortFileWriter(@Value("${app.data.dir:.}") String appDataDir) {
        this.appDataDir = appDataDir;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String token = SecurityConfig.getHandshakeToken();

        try {
            Path finalPath = Paths.get(appDataDir).resolve(PORT_FILE).toAbsolutePath().normalize();
            Path tempPath = Paths.get(appDataDir).resolve(TEMP_PORT_FILE).toAbsolutePath().normalize();

            Files.createDirectories(finalPath.getParent());

            String content = port + ":" + token;

            Files.writeString(tempPath, content);

            // Restrict temp file to owner-read/write only before moving into place
            try {
                Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(tempPath, ownerOnly);
            } catch (UnsupportedOperationException e) {
                // Non-POSIX filesystem (e.g. Windows); skip permission enforcement
                log.debug("POSIX file permissions not supported on this filesystem; skipping permission enforcement.");
            }

            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            log.info("Handshake data written atomically to {}", finalPath);
        } catch (IOException e) {
            log.error("Failed to write atomic handshake file: {}", PORT_FILE, e);
        }
    }
}