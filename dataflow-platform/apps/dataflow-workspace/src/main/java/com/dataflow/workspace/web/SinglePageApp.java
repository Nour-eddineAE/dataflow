package com.dataflow.workspace.web;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class SinglePageApp implements WebMvcConfigurer {

    private static final String INDEX = "index.html";
    private static final String ASSETS = "/assets/**";
    private static final Duration IMMUTABLE = Duration.ofDays(365);

    private final String[] locations;

    public SinglePageApp(WebProperties web) {
        this.locations = web.getResources().getStaticLocations();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ASSETS)
                .addResourceLocations(assets())
                .setCacheControl(CacheControl.maxAge(IMMUTABLE).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(locations)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new IndexFallback());
    }

    private String[] assets() {
        String[] assets = new String[locations.length];
        for (int i = 0; i < locations.length; i++) {
            assets[i] = locations[i] + "assets/";
        }
        return assets;
    }

    private static final class IndexFallback extends PathResourceResolver {

        @Override
        protected Resource getResource(@NonNull String path, Resource location) throws IOException {
            Resource resource = location.createRelative(path);
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            return path.startsWith("api/") || path.startsWith("actuator") ? null : location.createRelative(INDEX);
        }
    }
}
