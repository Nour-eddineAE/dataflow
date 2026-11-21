package com.dataflow.workspace.catalog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTest {

    @ParameterizedTest
    @CsvSource({
        "earthquakes, true",
        "lake_system, true",
        "lake_events, true",
        "system, false",
        "default, false",
        "serving, false",
        "INFORMATION_SCHEMA, false",
        "Hand-Made, false"
    })
    void onlyADatabaseADatasetWouldGetCountsAsADatasetDatabase(String database, boolean dataset) {
        assertThat(Catalog.isDatasetDatabase(database)).isEqualTo(dataset);
    }
}
