package com.dataflow.workspace.lineage;

import org.apache.spark.sql.catalyst.analysis.UnresolvedAlias;
import org.apache.spark.sql.catalyst.analysis.UnresolvedAttribute;
import org.apache.spark.sql.catalyst.analysis.UnresolvedRelation;
import org.apache.spark.sql.catalyst.analysis.UnresolvedStar;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.parser.CatalystSqlParser$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.SubqueryAlias;
import scala.jdk.javaapi.CollectionConverters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SqlReferences {

    private SqlReferences() {}

    static Set<Reference> expression(String sql) {
        Set<Reference> references = new LinkedHashSet<>();
        collect(CatalystSqlParser$.MODULE$.parseExpression(sql), references);
        return references;
    }

    static Output item(String sql) {
        return output(CatalystSqlParser$.MODULE$.parseExpression(sql));
    }

    static Query query(String sql) {
        LogicalPlan plan = CatalystSqlParser$.MODULE$.parsePlan(sql);
        Map<String, String> relations = new LinkedHashMap<>();
        relations(plan, relations);
        LogicalPlan projection = projection(plan);
        List<Output> outputs = new ArrayList<>();
        if (projection != null) {
            for (Expression expression : named(projection)) {
                outputs.add(output(expression));
            }
        }
        return new Query(outputs, relations);
    }

    private static Output output(Expression expression) {
        Set<Reference> inputs = new LinkedHashSet<>();
        if (expression instanceof UnresolvedStar star) {
            String qualifier = star.target().isDefined()
                    ? String.join(".", seq(star.target().get()))
                    : null;
            return new Output("*", qualifier == null ? List.of() : List.of(new Reference(qualifier, "*")), true);
        }
        if (expression instanceof Alias alias) {
            collect(alias.child(), inputs);
            return new Output(alias.name(), List.copyOf(inputs), false);
        }
        if (expression instanceof UnresolvedAttribute attribute) {
            List<String> parts = seq(attribute.nameParts());
            collect(attribute, inputs);
            return new Output(parts.getLast(), List.copyOf(inputs), false);
        }
        Expression inner = expression instanceof UnresolvedAlias alias ? alias.child() : expression;
        if (inner instanceof UnresolvedAttribute attribute) {
            collect(attribute, inputs);
            return new Output(seq(attribute.nameParts()).getLast(), List.copyOf(inputs), false);
        }
        collect(inner, inputs);
        return new Output(inner.toString().replace("'", ""), List.copyOf(inputs), false);
    }

    private static LogicalPlan projection(LogicalPlan plan) {
        LogicalPlan node = plan;
        while (node != null) {
            if (node instanceof Project || node instanceof Aggregate) {
                return node;
            }
            List<LogicalPlan> children = seq(node.children());
            node = children.size() == 1 ? children.getFirst() : null;
        }
        return null;
    }

    private static List<Expression> named(LogicalPlan projection) {
        List<NamedExpression> named = projection instanceof Project project
                ? seq(project.projectList())
                : seq(((Aggregate) projection).aggregateExpressions());
        List<Expression> expressions = new ArrayList<>();
        named.forEach(expression -> expressions.add((Expression) expression));
        return expressions;
    }

    private static void relations(LogicalPlan plan, Map<String, String> relations) {
        if (plan instanceof SubqueryAlias alias && alias.child() instanceof UnresolvedRelation relation) {
            relations.put(alias.alias(), String.join(".", seq(relation.multipartIdentifier())));
            return;
        }
        if (plan instanceof UnresolvedRelation relation) {
            String name = String.join(".", seq(relation.multipartIdentifier()));
            relations.put(name, name);
            return;
        }
        for (LogicalPlan child : seq(plan.children())) {
            relations(child, relations);
        }
        for (Expression expression : seq(plan.expressions())) {
            subqueries(expression, relations);
        }
    }

    private static void subqueries(Expression expression, Map<String, String> relations) {
        if (expression instanceof org.apache.spark.sql.catalyst.expressions.SubqueryExpression subquery) {
            relations(subquery.plan(), relations);
        }
        for (Expression child : seq(expression.children())) {
            subqueries(child, relations);
        }
    }

    private static void collect(Expression expression, Set<Reference> references) {
        if (expression instanceof UnresolvedAttribute attribute) {
            List<String> parts = seq(attribute.nameParts());
            references.add(
                    parts.size() == 1
                            ? new Reference(null, parts.getFirst())
                            : new Reference(String.join(".", parts.subList(0, parts.size() - 1)), parts.getLast()));
            return;
        }
        for (Expression child : seq(expression.children())) {
            collect(child, references);
        }
    }

    private static <T> List<T> seq(scala.collection.Seq<T> seq) {
        return new ArrayList<>(CollectionConverters.asJava(seq));
    }

    record Reference(String qualifier, String column) {}

    record Output(String name, List<Reference> inputs, boolean star) {}

    record Query(List<Output> outputs, Map<String, String> relations) {}
}
