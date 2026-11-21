package com.dataflow.workspace.controlplane;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class ControlPlane {

    private final DSLContext sql;

    public ControlPlane(DataSource dataSource) {
        this.sql = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public DSLContext sql() {
        return sql;
    }
}
