package com.dataflow.spec;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComputePolicyTest {

    private static final ComputePolicy POLICY = new ComputePolicy("2g", 2, "4g", 4, 8);

    @Test
    void aSizeWithinEveryLimitPasses() {
        ComputeSpec compute = new ComputeSpec(new ComputeSpec.Executors(1, 8), "1800m", "4g", 2, 4, Map.of());

        assertThat(POLICY.violations(compute)).isEmpty();
    }

    @Test
    void everyValueAboveItsLimitIsNamed() {
        ComputeSpec compute = new ComputeSpec(new ComputeSpec.Executors(9, 12), "3g", "4097m", 3, 5, Map.of());

        assertThat(POLICY.violations(compute)).containsExactly(
                "compute.driver-memory 3g is above the install's limit of 2g",
                "compute.executor-memory 4097m is above the install's limit of 4g",
                "compute.driver-cores 3 is above the install's limit of 2",
                "compute.executor-cores 5 is above the install's limit of 4",
                "compute.executors.min 9 is above the install's limit of 8",
                "compute.executors.max 12 is above the install's limit of 8");
    }

    @Test
    void noPolicyAllowsAnything() {
        ComputeSpec compute = new ComputeSpec(new ComputeSpec.Executors(0, 500), "64g", "64g", 32, 32, Map.of());

        assertThat(ComputePolicy.NONE.violations(compute)).isEmpty();
        assertThat(ComputePolicy.NONE.limits()).isFalse();
        assertThat(POLICY.limits()).isTrue();
    }

    @Test
    void blankLimitsMeanNoLimit() {
        assertThat(new ComputePolicy(" ", null, "", null, null)).isEqualTo(ComputePolicy.NONE);
    }

    @Test
    void sizesCompareInMebibytes() {
        assertThat(ComputePolicy.mebibytes("1800m")).isEqualTo(1800);
        assertThat(ComputePolicy.mebibytes("2G")).isEqualTo(2048);
    }

    @Test
    void aMalformedLimitIsRefusedAtStart() {
        assertThatThrownBy(() -> new ComputePolicy("2 gigs", null, null, null, null))
                .hasMessageContaining("max-driver-memory must be a size such as 512m or 4g");
        assertThatThrownBy(() -> new ComputePolicy(null, 0, null, null, null))
                .hasMessageContaining("max-driver-cores must be 1 or more");
    }
}
