package com.dataflow.spec;

import java.util.Map;

public interface FileSource {

    String url();

    String path();

    String format();

    Map<String, String> options();
}
