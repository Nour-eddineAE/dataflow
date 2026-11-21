package com.dataflow.spec;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.dataformat.yaml.YAMLAnchorReplayingFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlConstraintsTest {

    @Test
    void deepMergeKeysRespectTheNestingLimit() throws Exception {
        String yaml = Files.readString(Path.of("src/test/resources/yaml/deep-merge.yml"));
        YAMLMapper mapper = new YAMLMapper(new YAMLAnchorReplayingFactory());

        assertThatThrownBy(() -> mapper.readTree(yaml))
                .isInstanceOf(StreamConstraintsException.class)
                .hasMessageContaining("nesting depth");
    }
}
