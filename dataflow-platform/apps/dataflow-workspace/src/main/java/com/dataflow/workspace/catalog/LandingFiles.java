package com.dataflow.workspace.catalog;

import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.QueryResult;
import org.jooq.Field;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.dataflow.workspace.serving.ClickHouseDsl.column;
import static org.jooq.impl.DSL.regexpReplaceFirst;
import static org.jooq.impl.DSL.select;

@Component
public class LandingFiles {

    private static final Field<String> PATH = column("_path", String.class);
    private static final Field<Long> SIZE = LakeFields.OBJECT_SIZE;
    private static final Field<LocalDateTime> TIME = column("_time", LocalDateTime.class);

    private final ClickHouse serving;
    private final LakeFunctions lake;

    public LandingFiles(ClickHouse serving, LakeFunctions lake) {
        this.serving = serving;
        this.lake = lake;
    }

    public QueryResult list(String key) {
        Field<LocalDateTime> modified = TIME.as("modified");
        return serving.fetch(select(regexpReplaceFirst(PATH, "^.*/", "").as("file"), SIZE.as("bytes"), modified)
                .from(lake.objects(key + "*"))
                .orderBy(modified.desc()));
    }
}
