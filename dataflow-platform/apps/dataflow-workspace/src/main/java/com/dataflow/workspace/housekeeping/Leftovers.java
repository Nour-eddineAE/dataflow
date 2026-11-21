package com.dataflow.workspace.housekeeping;

import com.dataflow.workspace.housekeeping.Candidate.Kind;

import java.util.List;

interface Leftovers {

    Kind kind();

    List<Candidate> candidates();

    void remove(List<String> keys);
}
