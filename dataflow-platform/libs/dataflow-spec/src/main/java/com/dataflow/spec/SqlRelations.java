package com.dataflow.spec;

import org.apache.spark.sql.catalyst.analysis.UnresolvedRelation;
import org.apache.spark.sql.catalyst.analysis.UnresolvedTableValuedFunction;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.SubqueryExpression;
import org.apache.spark.sql.catalyst.parser.CatalystSqlParser$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.UnresolvedWith;
import scala.jdk.javaapi.CollectionConverters;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class SqlRelations {

    private static final String SEPARATOR = ".";
    private static final Set<String> ROW_GENERATORS = Set.of(
            "range", "explode", "explode_outer", "inline", "inline_outer", "posexplode", "posexplode_outer", "stack",
            "json_tuple");

    private SqlRelations() {}

    public static Set<String> of(String sql) {
        Set<String> found = new LinkedHashSet<>();
        Set<String> defined = new HashSet<>();
        walk(CatalystSqlParser$.MODULE$.parsePlan(sql), found, defined);
        found.removeAll(defined);
        return found;
    }

    public static boolean hasSubquery(Expression expression) {
        return expression instanceof SubqueryExpression
                || CollectionConverters.asJava(expression.children()).stream().anyMatch(SqlRelations::hasSubquery);
    }

    private static void walk(LogicalPlan plan, Set<String> found, Set<String> defined) {
        if (plan instanceof UnresolvedTableValuedFunction function) {
            String name = normalize(String.join(SEPARATOR, CollectionConverters.asJava(function.name())));
            if (!ROW_GENERATORS.contains(name)) {
                found.add(name);
            }
        }
        if (plan instanceof UnresolvedRelation relation) {
            found.add(normalize(String.join(SEPARATOR, CollectionConverters.asJava(relation.multipartIdentifier()))));
        }
        if (plan instanceof UnresolvedWith with) {
            CollectionConverters.asJava(with.cteRelations()).forEach(definition -> {
                defined.add(normalize(definition._1()));
                walk(definition._2(), found, defined);
            });
        }
        CollectionConverters.asJava(plan.children()).forEach(child -> walk(child, found, defined));
        CollectionConverters.asJava(plan.expressions()).forEach(expression -> subqueries(expression, found, defined));
    }

    private static void subqueries(Expression expression, Set<String> found, Set<String> defined) {
        if (expression instanceof SubqueryExpression subquery) {
            walk(subquery.plan(), found, defined);
        }
        CollectionConverters.asJava(expression.children()).forEach(child -> subqueries(child, found, defined));
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
