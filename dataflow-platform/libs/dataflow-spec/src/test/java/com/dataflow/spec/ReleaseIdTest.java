package com.dataflow.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseIdTest {

    private static final String COMMIT = "a41c9e2f6b1d4e8a9c3b2f1e0d9c8b7a6f5e4d3c";

    @Test
    void parse_readsProjectAndCommit() {
        ReleaseId release = ReleaseId.parse("examples@" + COMMIT);

        assertThat(release.project()).isEqualTo("examples");
        assertThat(release.commit()).isEqualTo(COMMIT);
        assertThat(release.folder()).isEqualTo("examples/" + COMMIT);
        assertThat(release.shortCommit()).isEqualTo("a41c9e2f6b");
        assertThat(release).hasToString("examples@" + COMMIT);
    }

    @Test
    void parse_rejectsAShortCommitOrABadProject() {
        assertThatThrownBy(() -> ReleaseId.parse("examples@a41c9e2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("full commit id");
        assertThatThrownBy(() -> ReleaseId.parse("Examples@" + COMMIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project must match");
        assertThatThrownBy(() -> ReleaseId.parse("examples"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<project>@<commit>");
    }
}
