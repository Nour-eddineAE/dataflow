package com.dataflow.workspace.runs;

import java.util.List;

public record RunOutput(List<OutputLine> lines, boolean truncated) {}
