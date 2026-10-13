package com.core.beautyshop.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    @Value("${app.media.upload-dir:uploads}")
    private String uploadDirectory;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        List<String> locations = new ArrayList<>();

        addPath(locations, Paths.get(uploadDirectory).toAbsolutePath().normalize());
        addPath(locations, Paths.get(System.getProperty("user.home"), ".beautyshop", "uploads").toAbsolutePath().normalize());
        addPath(locations, Paths.get(System.getProperty("java.io.tmpdir"), "beautyshop-uploads").toAbsolutePath().normalize());

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(locations.toArray(new String[0]))
                .setCachePeriod(3600);
    }

    private void addPath(List<String> locations, Path path) {
        try {
            Files.createDirectories(path);
        } catch (Exception ignored) { }
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        locations.add("file:" + normalized);
        locations.add("file:///" + normalized);
    }
}
