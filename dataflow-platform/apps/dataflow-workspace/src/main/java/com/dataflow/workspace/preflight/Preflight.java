package com.dataflow.workspace.preflight;

import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SourceSpec;
import com.dataflow.spec.StepSpec;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.DraftAccess;
import com.dataflow.workspace.folders.FolderFiles;
import com.dataflow.workspace.folders.JavaChecks;
import com.dataflow.workspace.setup.Check;
import com.dataflow.workspace.setup.SetupChecks;
import com.dataflow.workspace.specs.SpecCheck;
import com.dataflow.workspace.specs.SpecStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class Preflight {

    private final SpecStore specs;
    private final SourceProbe sources;
    private final FolderFiles folders;
    private final JavaChecks java;
    private final SetupChecks services;
    private final DraftAccess drafts;

    public Preflight(
            SpecStore specs,
            SourceProbe sources,
            FolderFiles folders,
            JavaChecks java,
            SetupChecks services,
            DraftAccess drafts) {
        this.drafts = drafts;
        this.specs = specs;
        this.sources = sources;
        this.folders = folders;
        this.java = java;
        this.services = services;
    }

    public List<Finding> run(String dataset, String content, UUID folderId) {
        SpecCheck check = specs.check(content, dataset);
        if (!check.valid()) {
            int count = check.problems().size();
            return List.of(Finding.failed("Spec", count + (count == 1 ? " problem" : " problems") + " to fix first"));
        }
        PipelineSpec spec = check.spec();
        try {
            drafts.require(spec);
        } catch (AccessDeniedException e) {
            return List.of(Finding.failed("Access", e.getMessage()));
        }
        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.passed("Spec", "Valid"));
        if (spec.ingests()) {
            findings.add(sources.probe("Source", sample(spec.source())));
        }
        spec.references().forEach(reference -> findings.add(sources.probe("Reference " + reference.name(), reference)));
        findings.addAll(transformations(spec, folderId));
        findings.addAll(platform(spec));
        return findings;
    }

    private static SourceSpec sample(SourceSpec source) {
        return source.partitioned() ? source.forPartition(source.partitions().start()) : source;
    }

    private List<Finding> transformations(PipelineSpec spec, UUID folderId) {
        List<String> wanted = Stream.concat(
                        spec.silver().steps().stream().map(StepSpec::transformation),
                        spec.gold().stream().filter(GoldSpec::usesTransformation).map(GoldSpec::transformation))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        if (folderId == null) {
            return List.of(Finding.warning("Transformations", "Not checked: open the spec from its Git folder"));
        }
        JavaChecks.Result compiled = java.compile(folders.transformations(folderId));
        if (!compiled.problems().isEmpty()) {
            return List.of(Finding.failed("Transformations",
                    compiled.problems().size() + " compile problems in " + JavaChecks.FOLDER));
        }
        Set<String> found = compiled.ids();
        return wanted.stream()
                .map(id -> found.contains(id)
                        ? Finding.passed("Transformation " + id, "Compiles")
                        : Finding.failed("Transformation " + id, "No @DataTransformation " + id + " in " + JavaChecks.FOLDER))
                .toList();
    }

    private List<Finding> platform(PipelineSpec spec) {
        List<Check> checks = new ArrayList<>(List.of(services.tower(), services.lake()));
        if (!spec.serve().isEmpty()) {
            checks.add(services.clickHouse());
        }
        return checks.stream()
                .map(check -> check.ok() ? Finding.passed(check.name(), check.detail()) : Finding.failed(check.name(), check.detail()))
                .toList();
    }
}
