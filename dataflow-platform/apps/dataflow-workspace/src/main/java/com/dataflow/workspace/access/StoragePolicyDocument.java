package com.dataflow.workspace.access;

import com.dataflow.spec.PipelineSpec;

import com.dataflow.spec.lake.Layer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class StoragePolicyDocument {

    static final String NAME = "dataflow_datasets";
    private static final List<String> ANY_BUCKET = List.of("arn:aws:s3:::*");
    static final List<String> LAYERS = Stream.concat(
                    Arrays.stream(Layer.values()).map(Layer::segment), Stream.of("system/events"))
            .toList();

    private StoragePolicyDocument() {}

    static List<String> datasets(Caller caller, List<Grant> grants, List<PipelineSpec> specs) {
        Set<String> allowed = new LinkedHashSet<>();
        if (caller.admin()) {
            specs.forEach(spec -> allowed.add(spec.dataset()));
        } else {
            grants.stream().filter(StoragePolicyDocument::readsDataset)
                    .filter(grant -> caller.is(grant.principal()))
                    .forEach(grant -> allowed.add(grant.securable().dataset()));
        }
        return specs.stream().map(PipelineSpec::dataset).filter(allowed::contains).distinct().sorted().toList();
    }

    static List<String> prefixes(String dataset) {
        Securable.dataset(dataset);
        return LAYERS.stream().map(layer -> layer + "/" + dataset + "/").toList();
    }

    static Map<String, Object> build(List<Grant> grants, List<PipelineSpec> specs, List<String> admins, String bucket) {
        if (admins.isEmpty()) {
            throw new IllegalArgumentException("Storage access requires an administrator group");
        }
        String arn = "arn:aws:s3:::" + bucket;
        List<Map<String, Object>> statements = new ArrayList<>();
        Map<String, Object> administrator = Map.of("ForAnyValue:StringEquals", Map.of("jwt:groups", groupNames(admins)));
        statements.add(allow(List.of("admin:*"), ANY_BUCKET, administrator));
        statements.add(allow(List.of("s3:*"), ANY_BUCKET, administrator));
        Set<String> known = new LinkedHashSet<>(specs.stream().map(PipelineSpec::dataset).toList());
        Map<Principal, Set<String>> wanted = new LinkedHashMap<>();
        grants.stream().filter(StoragePolicyDocument::readsDataset)
                .filter(grant -> known.contains(grant.securable().dataset()))
                .forEach(grant -> wanted.computeIfAbsent(grant.principal(), ignored -> new LinkedHashSet<>())
                        .add(grant.securable().dataset()));
        wanted.forEach((principal, datasets) -> {
            List<String> prefixes = datasets.stream().sorted().flatMap(dataset -> prefixes(dataset).stream())
                    .map(prefix -> prefix + "*").toList();
            Map<String, Object> identity = principal.kind() == Principal.Kind.GROUP
                    ? Map.of("ForAnyValue:StringEquals", Map.of("jwt:groups", groupNames(List.of(principal.name()))))
                    : Map.of("StringEquals", Map.of("jwt:preferred_username", List.of(principal.name())));
            statements.add(allow(List.of("s3:ListAllMyBuckets"), ANY_BUCKET, identity));
            statements.add(allow(List.of("s3:GetBucketLocation"), List.of(arn), identity));
            Map<String, Object> listing = new LinkedHashMap<>(identity);
            listing.put("StringLike", Map.of("s3:prefix", prefixes));
            statements.add(allow(List.of("s3:ListBucket"), List.of(arn), listing));
            statements.add(allow(List.of("s3:GetObject", "s3:GetObjectVersion"),
                    prefixes.stream().map(prefix -> arn + "/" + prefix).toList(), identity));
        });
        return Map.of("Version", "2012-10-17", "Statement", statements);
    }

    private static boolean readsDataset(Grant grant) {
        return grant.securable().kind() == Securable.Kind.DATASET
                && (grant.privilege() == Privilege.SELECT || grant.privilege() == Privilege.MANAGE);
    }

    private static List<String> groupNames(List<String> groups) {
        return groups.stream().flatMap(group -> {
            String plain = group.startsWith("/") ? group.substring(1) : group;
            return List.of(plain, "/" + plain).stream();
        }).distinct().toList();
    }

    private static Map<String, Object> allow(List<String> actions, List<String> resources, Map<String, Object> condition) {
        return Map.of("Effect", "Allow", "Action", actions, "Resource", resources, "Condition", condition);
    }
}
