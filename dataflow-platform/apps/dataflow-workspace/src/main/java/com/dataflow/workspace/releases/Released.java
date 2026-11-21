package com.dataflow.workspace.releases;

import java.util.Map;

public record Released(String project, String target, String person, Map<String, String> owners) {}
