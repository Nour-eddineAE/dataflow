package com.dataflow.workspace.specs;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class Parsing {

    private static final long STACK_BYTES = 64L * 1024 * 1024;
    private static final int THREADS = 2;

    private final AtomicInteger counter = new AtomicInteger();
    private final ExecutorService pool = Executors.newFixedThreadPool(THREADS, task -> {
        Thread thread = new Thread(null, task, "spec-parser-" + counter.incrementAndGet(), STACK_BYTES);
        thread.setDaemon(true);
        return thread;
    });

    public <T> T run(Supplier<T> work) {
        try {
            return pool.submit(work::get).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while parsing a spec", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Parsing failed: " + e.getCause(), e.getCause());
        }
    }

    @PreDestroy
    public void stop() {
        pool.shutdownNow();
    }
}
