package com.dataflow.engine.core.draft;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamesTest {

    @Test
    void sourceNamesBecomeCamelCase() {
        assertThat(Names.camel("PULocationID")).isEqualTo("puLocationID");
        assertThat(Names.camel("tpep_pickup_datetime")).isEqualTo("tpepPickupDatetime");
        assertThat(Names.camel("Trip Distance")).isEqualTo("tripDistance");
        assertThat(Names.camel("VendorID")).isEqualTo("vendorID");
        assertThat(Names.camel("URL")).isEqualTo("url");
    }
}
