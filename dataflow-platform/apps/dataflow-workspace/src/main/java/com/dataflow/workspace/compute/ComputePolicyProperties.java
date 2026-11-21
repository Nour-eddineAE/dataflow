package com.dataflow.workspace.compute;

import com.dataflow.spec.ComputePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dataflow.workspace.compute-policy")
public record ComputePolicyProperties(
        String maxDriverMemory,
        Integer maxDriverCores,
        String maxExecutorMemory,
        Integer maxExecutorCores,
        Integer maxExecutors) {

    public ComputePolicy policy() {
        return new ComputePolicy(maxDriverMemory, maxDriverCores, maxExecutorMemory, maxExecutorCores, maxExecutors);
    }
}
