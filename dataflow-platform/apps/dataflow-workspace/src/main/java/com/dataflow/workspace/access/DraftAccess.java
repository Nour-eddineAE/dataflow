package com.dataflow.workspace.access;

import com.dataflow.spec.FileSource;
import com.dataflow.spec.InternalAddress;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SourceSpec;
import com.dataflow.spec.TableRef;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;
import com.dataflow.workspace.config.SourcesProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

@Component
public class DraftAccess {

    private static final Set<String> FILE_FORMATS = Set.of("csv", "json", "parquet", "orc", "avro", "text", "xml");
    private static final Set<String> WEB = Set.of("http", "https");
    private static final Set<String> LAYERS =
            Set.copyOf(Arrays.stream(Layer.values()).map(Layer::segment).toList());

    private final Access access;
    private final SourcesProperties settings;

    public DraftAccess(Access access, SourcesProperties settings) {
        this.access = access;
        this.settings = settings;
    }

    public void require(PipelineSpec spec) {
        require(spec, dataset -> false);
    }

    public void require(PipelineSpec spec, Predicate<String> sameProject) {
        Caller caller = access.caller();
        if (caller.admin()) {
            return;
        }
        spec.inputs().values().stream()
                .map(TableRef::parse)
                .flatMap(java.util.Optional::stream)
                .filter(table -> !sameProject.test(table.dataset()))
                .forEach(table -> access.require(Privilege.SELECT, new Securable(Securable.Kind.TABLE, table.toString())));
        if (spec.ingests()) {
            source(caller, spec.source());
        }
        spec.references().forEach(reference -> source(caller, reference));
    }

    public void requireSource(String location) {
        Caller caller = access.caller();
        String trimmed = location == null ? "" : location.strip();
        if (caller.admin() || trimmed.isEmpty()) {
            return;
        }
        if (Lake.KEY.matcher(trimmed).matches()) {
            lakeKey(caller, trimmed);
        } else {
            url(caller, trimmed);
        }
    }

    private void lakeKey(Caller caller, String key) {
        if (key.startsWith(Lake.uploadsOf(caller.id()))) {
            return;
        }
        String[] segments = key.split("/");
        if (segments.length < 2 || !LAYERS.contains(segments[0])) {
            throw denied(caller, "read " + key);
        }
        access.require(Privilege.SELECT, Securable.dataset(segments[1]));
    }

    private void source(Caller caller, FileSource source) {
        if (!FILE_FORMATS.contains(source.format().toLowerCase(Locale.ROOT))) {
            throw denied(caller, "read the format " + source.format());
        }
        if (source.url() != null) {
            url(caller, source.url().replace(SourceSpec.PARTITION_PLACEHOLDER, "x"));
        } else if (!ownUpload(caller, source.path())
                && settings.allowedPaths().stream().noneMatch(source.path()::startsWith) || source.path().contains("..")) {
            throw denied(caller, "read the path " + source.path());
        }
    }

    private static boolean ownUpload(Caller caller, String path) {
        String key = path.replaceFirst("^s3a?://[^/]+/", "");
        return key.startsWith(Lake.uploadsOf(caller.id())) && !key.contains("..");
    }

    private void url(Caller caller, String text) {
        URI uri = URI.create(text);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!WEB.contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT)) || host.isEmpty()) {
            throw denied(caller, "read " + text);
        }
        if (!settings.allowedHosts().contains(host) && InternalAddress.is(host)) {
            throw denied(caller, "reach the internal address " + host);
        }
    }

    private static AccessDeniedException denied(Caller caller, String what) {
        return new AccessDeniedException(caller.id() + " may not " + what + "; an administrator can allow it");
    }
}
