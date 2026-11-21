package com.dataflow.workspace.catalog;

import com.dataflow.workspace.serving.ClickHouse;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;

@Component
public class Quarantine {

    private static final String SOURCE = "source";
    private static final String FAILED = "failedExpectations";

    private final ClickHouse serving;

    public Quarantine(ClickHouse serving) {
        this.serving = serving;
    }

    public List<RuleCount> reasons(Select<Record> source) {
        Field<String> rule =
                function("arrayJoin", String.class, field(name(SOURCE, FAILED))).as("rule");
        Field<Integer> rows = count().as("rows");
        return serving.fetchInto(
                select(rule, rows).from(source.asTable(SOURCE)).groupBy(rule).orderBy(rows.desc()), RuleCount.class);
    }
}
