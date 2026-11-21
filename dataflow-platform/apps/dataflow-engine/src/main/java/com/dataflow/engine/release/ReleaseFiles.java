package com.dataflow.engine.release;

import com.dataflow.spec.ReleaseId;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.RemoteIterator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class ReleaseFiles {


    private static final String HADOOP_PREFIX = "spark.hadoop.";

    private final String root;
    private final Configuration hadoop = new Configuration();

    public ReleaseFiles(String root, Map<String, String> sparkConfig) {
        this.root = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        sparkConfig.forEach((key, value) -> {
            if (key.startsWith(HADOOP_PREFIX)) {
                hadoop.set(key.substring(HADOOP_PREFIX.length()), value);
            }
        });
    }

    public Path materialize(ReleaseId release) {
        org.apache.hadoop.fs.Path source = new org.apache.hadoop.fs.Path(root + "/" + release.folder());
        try {
            FileSystem store = source.getFileSystem(hadoop);
            if (!store.exists(new org.apache.hadoop.fs.Path(source, ReleaseId.MANIFEST))) {
                throw new IllegalStateException(
                        "Release " + release + " is missing or incomplete: no " + ReleaseId.MANIFEST + " under " + source);
            }
            Path target = Files.createTempDirectory("release-" + release.shortCommit())
                    .resolve(release.project());
            String prefix = store.makeQualified(source).toUri().getPath();
            RemoteIterator<LocatedFileStatus> files = store.listFiles(source, true);
            int count = 0;
            while (files.hasNext()) {
                org.apache.hadoop.fs.Path file = files.next().getPath();
                Path local = target.resolve(file.toUri().getPath().substring(prefix.length() + 1));
                Files.createDirectories(local.getParent());
                try (InputStream in = store.open(file)) {
                    Files.copy(in, local);
                }
                count++;
            }
            log.info("Release {} read from {}: {} files", release, source, count);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read release " + release + " from " + source, e);
        }
    }
}
