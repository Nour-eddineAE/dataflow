package com.dataflow.workspace.compute;

import com.dataflow.spec.ComputePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ComputeConfiguration {

    @Bean
    ComputePolicy computePolicy(ComputePolicyProperties properties) {
        return properties.policy();
    }
}
