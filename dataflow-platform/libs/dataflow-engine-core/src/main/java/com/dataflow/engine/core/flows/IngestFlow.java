package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.run.AssetKeys;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowResult;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.spec.SourceSpec;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("resource")
public class IngestFlow implements Flow {

    static final int HASH_CHARS = 16;
    private static final int BUFFER = 1 << 16;

    private final Sources sources;

    static String landedName(String fileName, String hash) {
        String stamp = hash.substring(0, HASH_CHARS);
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName + "." + stamp : fileName.substring(0, dot) + "." + stamp + fileName.substring(dot);
    }

    private static List<Path> glob(Path pattern, Configuration conf) {
        try {
            FileStatus[] matches = pattern.getFileSystem(conf).globStatus(pattern);
            if (matches == null || matches.length == 0) {
                throw new IllegalStateException("No source file matches " + pattern);
            }
            List<Path> files = new ArrayList<>();
            for (FileStatus match : matches) {
                if (match.isFile()) {
                    files.add(match.getPath());
                }
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + pattern, e);
        }
    }

    private static String sha256(FileSystem fs, Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[BUFFER];
        try (FSDataInputStream in = fs.open(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override
    public String name() {
        return FlowNames.INGEST;
    }

    @Override
    public Set<String> inputs() {
        return Set.of();
    }

    @Override
    public FlowResult run(RunContext context) {
        SourceSpec source = context.plan().source();
        checkPartition(context, source);
        SourceSpec resolved = source.forPartition(context.partition());
        Configuration conf = context.spark().sparkContext().hadoopConfiguration();
        Path landing = new Path(context.lake().table(Layer.LANDING, context.dataset()));
        List<Path> files = files(context, resolved, conf);
        Landed landed = land(context, files, landing, conf);
        log.info(
                "Landed {} new file(s) for {} ({} bytes); {} already landed",
                landed.names().size(),
                context.dataset(),
                landed.bytes(),
                landed.known().size());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("landed_files", landed.names().size());
        metadata.put("already_landed", landed.known().size());
        metadata.put("bytes", landed.bytes());
        metadata.put("location", landing.toString());
        if (context.partition() != null) {
            metadata.put("partition", context.partition());
        }
        return new FlowResult()
                .rows(files.size(), landed.names().size(), 0)
                .detail("landed", landed.names())
                .detail("already_landed", landed.known())
                .materialized(AssetKeys.of(context.dataset(), Layer.LANDING), metadata);
    }

    private static void checkPartition(RunContext context, SourceSpec source) {
        if (!source.partitioned()) {
            return;
        }
        String kind = source.partitions().kind().name().toLowerCase(Locale.ROOT);
        if (context.partition() == null) {
            throw new IllegalArgumentException(context.dataset() + " is partitioned " + kind + "; pass a partition key");
        }
        if (!source.partitions().kind().accepts(context.partition())) {
            throw new IllegalArgumentException("Partition '" + context.partition() + "' is not a " + kind + " key");
        }
    }

    private List<Path> files(RunContext context, SourceSpec resolved, Configuration conf) {
        if (resolved.url() != null) {
            java.nio.file.Path local = sources.fetch(context.dataset(), resolved, resolved.partitioned());
            return List.of(new Path(local.toUri()));
        }
        return glob(new Path(resolved.path()), conf);
    }

    private static Landed land(RunContext context, List<Path> files, Path landing, Configuration conf) {
        List<String> names = new ArrayList<>();
        List<String> known = new ArrayList<>();
        long bytes = 0;
        try {
            FileSystem target = landing.getFileSystem(conf);
            for (Path file : files) {
                FileSystem from = file.getFileSystem(conf);
                Path destination = new Path(landing, landedName(file.getName(), sha256(from, file)));
                if (target.exists(destination)) {
                    known.add(destination.getName());
                    continue;
                }
                FileUtil.copy(from, file, target, destination, false, true, conf);
                bytes += from.getFileStatus(file).getLen();
                names.add(destination.getName());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Landing " + context.dataset() + " failed", e);
        }
        return new Landed(names, known, bytes);
    }

    private record Landed(List<String> names, List<String> known, long bytes) {}
}
