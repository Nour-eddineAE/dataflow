package com.dataflow.engine.core.transform;

import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Repartition;
import org.apache.spark.sql.catalyst.plans.logical.RepartitionByExpression;
import scala.runtime.BoxedUnit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PlanChecks {

    static final int LARGE_PLAN = 400;
    private static final Set<String> AGGREGATING = Set.of("Aggregate", "Window", "Deduplicate", "Distinct");
    private static final Set<String> TYPED = Set.of(
            "MapElements",
            "MapPartitions",
            "DeserializeToObject",
            "FlatMapGroupsInPandas",
            "AppendColumns",
            "TypedFilter");

    private PlanChecks() {}

    public static Findings check(LogicalPlan plan, long jobs) {
        return check(plan, jobs, Placement.SILVER);
    }

    public static Findings check(LogicalPlan plan, long jobs, Placement placement) {
        boolean silver = placement == Placement.SILVER;
        Set<String> errors = new LinkedHashSet<>();
        Set<String> warnings = new LinkedHashSet<>();
        int nodes = size(plan);
        if (nodes > LARGE_PLAN) {
            warnings.add("the plan has " + nodes
                    + " steps: withColumn in a loop? withColumns adds many columns in one step");
        }
        if (jobs > 0) {
            warnings.add(jobs + " Spark job(s) ran while the plan was built: an action (collect, count, show, first)"
                    + " inside apply runs on the data every batch");
        }
        plan.foreach(node -> {
            node(node, silver, errors, warnings);
            return BoxedUnit.UNIT;
        });
        return new Findings(new ArrayList<>(errors), new ArrayList<>(warnings));
    }

    private static int size(LogicalPlan plan) {
        return plan.collect(new scala.runtime.AbstractPartialFunction<LogicalPlan, LogicalPlan>() {
                    @Override
                    public boolean isDefinedAt(LogicalPlan node) {
                        return true;
                    }

                    @Override
                    public LogicalPlan apply(LogicalPlan node) {
                        return node;
                    }
                })
                .size();
    }

    private static void node(LogicalPlan node, boolean silver, Set<String> errors, Set<String> warnings) {
        String kind = node.getClass().getSimpleName();
        if (silver && AGGREGATING.contains(kind)) {
            errors.add(kind + " in a silver step works on one micro-batch, not the whole table; aggregate in gold");
        }
        if (singlePartition(node)) {
            warnings.add("coalesce(1) or repartition(1) sends every row through one task");
        }
        if (silver && kind.equals("Sort")) {
            warnings.add("a sort inside a step orders one micro-batch only and costs a shuffle");
        }
        if (TYPED.contains(kind)) {
            warnings.add(kind + ": a typed lambda (map, mapPartitions, filter with a function) is opaque to Spark's"
                    + " optimizer; prefer Column expressions");
        }
        if (node instanceof Join join && join.condition().isEmpty() && !join.joinType().sql().contains("CROSS")) {
            warnings.add("a join without a condition is a cross join: every row meets every row");
        }
        node.expressions().foreach(expression -> {
            if (!expression.deterministic()) {
                warnings.add("a non-deterministic expression ("
                        + origin(expression).prettyName() + ") gives a different result when a batch is replayed");
            }
            return BoxedUnit.UNIT;
        });
    }

    private static boolean singlePartition(LogicalPlan node) {
        return node instanceof Repartition repartition && repartition.numPartitions() == 1
                || node instanceof RepartitionByExpression byExpression && byExpression.optNumPartitions().contains(1);
    }

    private static Expression origin(Expression expression) {
        scala.collection.Iterator<Expression> children = expression.children().iterator();
        while (children.hasNext()) {
            Expression child = children.next();
            if (!child.deterministic()) {
                return origin(child);
            }
        }
        return expression;
    }

    public enum Placement {
        SILVER,
        GOLD
    }

    public record Findings(List<String> errors, List<String> warnings) {

        public boolean failed() {
            return !errors.isEmpty();
        }

        public Findings without(Findings upstream) {
            return new Findings(
                    errors.stream()
                            .filter(error -> !upstream.errors().contains(error))
                            .toList(),
                    warnings.stream()
                            .filter(warning -> !upstream.warnings().contains(warning))
                            .toList());
        }
    }
}
