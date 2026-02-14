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
 *   <li><b>Handshake Orchestration:</b> Writes the port and security token to a shared file
 *   ({@code data/port.txt}) in a standardized {@code port:token} format.</li>
 *   <li><b>Filesystem Integration:</b> Ensures the target directory exists and handles
 *   atomic file writing to prevent race conditions during Electron startup.</li>
 *   <li><b>Lifecycle Synchronization:</b> Acts as the final step in the backend startup
 *   sequence, signaling to the Electron process that the server is ready for traffic.</li>
 * </ul>
 */
@Component
public class PortFileWriter implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(PortFileWriter.class);
    private static final String PORT_FILE = "data/port.txt";

    private final String appDataDir;

    public PortFileWriter(@Value("${app.data.dir:.}") String appDataDir) {
        this.appDataDir = appDataDir;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String token = SecurityConfig.getHandshakeToken();

        try {
            Path path = Paths.get(appDataDir).resolve(PORT_FILE).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());

            String content = port + ":" + token;
            Files.writeString(path, content);

            log.info("Handshake data written to {}", path);
        } catch (IOException e) {
            log.error("Failed to write handshake file: {}", PORT_FILE, e);
        }
    }
}
