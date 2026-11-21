package com.dataflow.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathTest {

    private static final String SPARK_CONF = "org/apache/spark/SparkConf.class";

    @Test
    void sparkConfComesFromSparkCoreOnly() throws IOException {
        List<URL> copies = Collections.list(getClass().getClassLoader().getResources(SPARK_CONF));

        assertThat(copies).singleElement().asString().contains("spark-core_");
    }
}
