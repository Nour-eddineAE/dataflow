package com.dataflow.engine.core.serving;

import java.util.List;

public interface Warehouse {

    void execute(String sql);

    List<List<String>> query(String sql);
}
