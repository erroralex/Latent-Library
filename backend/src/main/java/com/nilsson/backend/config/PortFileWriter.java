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
 * Listener that writes the actual server port to a file once the web server is initialized.
 * This is used by the Electron frontend to reliably discover the dynamic port assigned by Spring Boot.
 */
@Component
public class PortFileWriter implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PortFileWriter.class);
    private static final String PORT_FILE = "data/port.txt";
    
    private final String appDataDir;

    public PortFileWriter(@Value("${app.data.dir:.}") String appDataDir) {
        this.appDataDir = appDataDir;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        try {
            Path path = Paths.get(appDataDir).resolve(PORT_FILE).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Files.writeString(path, String.valueOf(port));
            logger.info("Server port {} written to {}", port, path);
        } catch (IOException e) {
            logger.error("Failed to write port to file: {}", PORT_FILE, e);
        }
    }
}
