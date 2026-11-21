package com.dataflow.workspace.impact;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.datasets.PipelineGraphs;
import com.dataflow.workspace.lineage.ColumnLineage;
import com.dataflow.workspace.specs.SpecCheck;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.specs.StoredSpec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DraftReview {

    private final SpecStore specs;

    public DraftReview(SpecStore specs) {
        this.specs = specs;
    }

    public DraftCheck review(String dataset, String content, boolean comparesWithSaved) {
        SpecCheck check = specs.check(content == null ? "" : content, dataset);
        if (!check.valid()) {
            return new DraftCheck(false, check.problems(), List.of(), List.of(), null, null);
        }
        PipelineSpec draft = check.spec();
        PipelineSpec saved = dataset == null || !comparesWithSaved
                ? null
                : specs.find(dataset)
                        .filter(StoredSpec::valid)
                        .map(StoredSpec::spec)
                        .orElse(null);
        List<String> warnings =
                specs.parsing().run(() -> ColumnLineage.of(draft)).warnings();
        return new DraftCheck(
                true,
                List.of(),
                warnings,
                SpecImpact.compare(saved, draft),
                draft.dataset(),
                PipelineGraphs.of(draft, Map.of()));
    }
}
