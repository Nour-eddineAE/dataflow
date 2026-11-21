package com.dataflow.workspace.catalog;

import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.Names;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.ReferenceSpec;
import com.dataflow.spec.ServeSpec;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;
import com.dataflow.workspace.serving.LakeFunctions;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.jooq.impl.DSL.asterisk;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;

public final class Catalog {

    public static final String LANDING = Layer.LANDING.segment();
    public static final String BRONZE = Layer.BRONZE.segment();
    public static final String SILVER = Layer.SILVER.segment();
    public static final String QUARANTINE = Layer.QUARANTINE.segment();
    public static final String HISTORY = Layer.HISTORY.segment();
    public static final String CURRENT = ServeSpec.CURRENT;
    public static final String EVENTS = "events";
    public static final String GOLD = Layer.GOLD.segment();
    public static final String REFERENCE = Layer.REFERENCE.segment();
    public static final String INPUT = "input";
    public static final String SERVING = "serving";

    private static final Set<String> RESERVED_DATABASES = Set.of("system", "default", SERVING, "information_schema");
    private static final String RESERVED_PREFIX = "lake_";

    private Catalog() {}

    public static String database(String dataset) {
        return RESERVED_DATABASES.contains(dataset.toLowerCase(Locale.ROOT)) ? RESERVED_PREFIX + dataset : dataset;
    }

    public static boolean isDatasetDatabase(String database) {
        return holds(database, database)
                || database.startsWith(RESERVED_PREFIX)
                        && holds(database.substring(RESERVED_PREFIX.length()), database);
    }

    private static boolean holds(String dataset, String database) {
        return Names.DATASET.matcher(dataset).matches() && database(dataset).equals(database);
    }

    private static List<LakeTable> ingestion(PipelineSpec spec, String ds, String db) {
        List<LakeTable> tables = new ArrayList<>();
        tables.add(new LakeTable(
                LANDING, LANDING, LANDING, Lake.key(Layer.LANDING, ds), null,
                "Raw files as fetched, named by content hash", false, false, 0, null));
        tables.add(stage(BRONZE, Layer.BRONZE, ds, db,
                "Rows as received, every source column kept as text, plus _rescued_data"));
        tables.add(stage(SILVER, Layer.SILVER, ds, db, "Typed, derived and checked rows, one per arrival"));
        if (spec.quarantines()) {
            tables.add(stage(QUARANTINE, Layer.QUARANTINE, ds, db, "Rows a drop rule rejected, with the rules they broke"));
        }
        tables.add(stage(HISTORY, Layer.HISTORY, ds, db, "Every version of every fact (SCD2), change feed on"));
        tables.add(new LakeTable(
                CURRENT, HISTORY, CURRENT, Lake.key(Layer.HISTORY, ds), db + "." + CURRENT,
                "The current version of each fact (history where __END_AT IS NULL)", true, false, 0, null));
        return tables;
    }

    private static LakeTable stage(String id, Layer layer, String ds, String db, String description) {
        return delta(id, id, id, Lake.key(layer, ds), db, id, description);
    }

    public static List<LakeTable> tables(PipelineSpec spec) {
        String ds = spec.dataset();
        String db = database(ds);
        List<LakeTable> tables = spec.ingests() ? ingestion(spec, ds, db) : new ArrayList<>();
        for (ReferenceSpec reference : spec.references()) {
            tables.add(delta(
                    REFERENCE + "." + reference.name(),
                    REFERENCE,
                    reference.name(),
                    Lake.key(Layer.REFERENCE, ds, reference.name()),
                    db,
                    REFERENCE + "_" + reference.name(),
                    "Lookup table, reloaded every run"));
        }
        for (GoldSpec gold : spec.materialized()) {
            tables.add(delta(
                    GOLD + "." + gold.name(),
                    GOLD,
                    gold.name(),
                    Lake.key(Layer.GOLD, ds, gold.name()),
                    db,
                    GOLD + "_" + gold.name(),
                    "Gold table, rebuilt every run"));
        }
        tables.add(delta(
                EVENTS,
                "system",
                "events",
                Lake.eventsKey(ds),
                db,
                EVENTS,
                "Event log: what every flow of every run did"));
        return tables;
    }

    public static Optional<LakeTable> table(PipelineSpec spec, String id) {
        return tables(spec).stream().filter(table -> table.id().equals(id)).findFirst();
    }

    public static Select<Record> source(LakeFunctions lake, LakeTable table) {
        Table<Record> delta = lake.delta(table.key());
        if (CURRENT.equals(table.id())) {
            return select(asterisk().except(LakeFields.CONTENT_HASH, LakeFields.END_AT))
                    .from(delta)
                    .where(LakeFields.END_AT.isNull());
        }
        return selectFrom(delta);
    }

    private static LakeTable delta(
            String id, String layer, String label, String key, String db, String table, String description) {
        return new LakeTable(id, layer, label, key, db + "." + table, description, true, false, 0, null);
    }
}
