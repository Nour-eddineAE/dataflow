package com.dataflow.workspace.web;

import com.dataflow.spec.Names;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.impact.DraftCheck;
import com.dataflow.workspace.impact.DraftReview;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.setup.PlatformServices;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.specs.StoredSpec;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SpecController {

    private final SpecStore specs;
    private final Tower tower;
    private final SpecTemplate template;
    private final PlatformServices services;
    private final DraftReview review;

    private final Access access;

    public SpecController(
            SpecStore specs,
            Tower tower,
            SpecTemplate template,
            DraftReview review,
            Access access,
            PlatformServices services) {
        this.services = services;
        this.access = access;
        this.specs = specs;
        this.tower = tower;
        this.template = template;
        this.review = review;
    }

    private static String checkedDataset(String dataset) {
        if (dataset == null || !Names.DATASET.matcher(dataset).matches()) {
            throw new IllegalArgumentException(
                    "A dataset name is lower case letters, digits and _, starting with a letter");
        }
        return dataset;
    }

    @GetMapping("/specs/{dataset}")
    public StoredSpec spec(@PathVariable String dataset) {
        String name = checkedDataset(dataset);
        access.require(Privilege.SELECT, Securable.dataset(name));
        return specs.get(name);
    }

    @PostMapping("/specs/check")
    public DraftCheck check(@RequestBody Draft draft) {
        String name = checkedDataset(draft.dataset());
        return review.review(name, draft.content(), access.sees(name));
    }

    @PostMapping("/specs/tree")
    public Map<String, Object> tree(@RequestBody Draft draft) {
        return specs.tree(draft.content());
    }

    @PostMapping("/tower/reload")
    public Reloaded reload() {
        services.requireManage(PlatformServices.TOWER);
        tower.reload();
        return new Reloaded(true);
    }

    @GetMapping("/specs/template")
    public Template template(@RequestParam String dataset) {
        return new Template(template.render(checkedDataset(dataset)));
    }

    public record Draft(String dataset, String content) {}

    public record Reloaded(boolean reloaded) {}

    public record Template(String content) {}
}
