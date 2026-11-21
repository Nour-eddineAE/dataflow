import json
import os
import sys
import time
import traceback

from pyspark.sql import SparkSession, functions as F

ROOT = "s3a://lake/s1"
RESULTS = []


def record(name, ok, seconds, detail=""):
    RESULTS.append({"test": name, "ok": ok, "seconds": round(seconds, 2), "detail": str(detail)[:600]})
    print(f"S1RESULT {json.dumps(RESULTS[-1])}", flush=True)


def step(name):
    def wrap(fn):
        t = time.time()
        try:
            detail = fn()
            record(name, True, time.time() - t, detail if detail is not None else "")
        except Exception as e:
            record(name, False, time.time() - t, f"{type(e).__name__}: {e}")
            traceback.print_exc()
        return fn

    return wrap


spark = (
    SparkSession.builder.appName("s1-hangar")
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
    .config("spark.hadoop.fs.s3a.endpoint", "http://rustfs:9000")
    .config("spark.hadoop.fs.s3a.endpoint.region", "us-east-1")
    .config("spark.hadoop.fs.s3a.path.style.access", "true")
    .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
    .config("spark.hadoop.fs.s3a.access.key", os.environ["RUSTFS_ACCESS_KEY"])
    .config("spark.hadoop.fs.s3a.secret.key", os.environ["RUSTFS_SECRET_KEY"])
    .config("spark.hadoop.fs.s3a.buffer.dir", "/tmp")
    .config("spark.sql.streaming.checkpointFileManagerClass",
            "org.apache.spark.internal.io.cloud.AbortableStreamBasedCheckpointFileManager")
    .config("spark.databricks.delta.retentionDurationCheck.enabled", "false")
    .config("spark.sql.shuffle.partitions", "8")
    .config("spark.databricks.delta.snapshotPartitions", "4")
    .config("spark.ui.enabled", "false")
    .getOrCreate()
)
spark.sparkContext.setLogLevel("WARN")
record("versions", True, 0, f"spark={spark.version}")

HIST = f"{ROOT}/history"
BRONZE = f"{ROOT}/bronze"


@step("create table: timestamp_ntz + CDF + deletion vectors + CLUSTER BY")
def _():
    spark.sql(f"""
        CREATE OR REPLACE TABLE delta.`{HIST}` (
          factUniqueId STRING, name STRING, amount DOUBLE, occurredAt TIMESTAMP_NTZ,
          contentHash STRING, __START_AT TIMESTAMP, __END_AT TIMESTAMP)
        USING DELTA CLUSTER BY (factUniqueId)
        TBLPROPERTIES ('delta.enableChangeDataFeed'='true', 'delta.enableDeletionVectors'='true')""")
    d = spark.sql(f"DESCRIBE DETAIL delta.`{HIST}`").select("clusteringColumns", "tableFeatures").first()
    return f"clustering={d[0]} features={d[1]}"


def batch(rows):
    return (spark.createDataFrame(rows, "factUniqueId string, name string, amount double, occurredAt string")
            .withColumn("occurredAt", F.col("occurredAt").cast("timestamp_ntz"))
            .withColumn("contentHash", F.sha2(F.concat_ws("\u001f", "name", "amount"), 256)))


@step("append")
def _():
    (batch([(f"k{i}", f"n{i}", float(i), f"2026-10-0{1 + i % 9} 10:00:00") for i in range(1000)])
     .withColumn("__START_AT", F.current_timestamp()).withColumn("__END_AT", F.lit(None).cast("timestamp"))
     .write.format("delta").mode("append").save(HIST))
    return f"rows={spark.read.format('delta').load(HIST).count()}"


def scd2_merge(src):
    src.createOrReplaceTempView("chunk")
    spark.read.format("delta").load(HIST).createOrReplaceTempView("hist")
    spark.sql(f"""
        MERGE INTO delta.`{HIST}` t
        USING (
          SELECT NULL AS mergeKey, s.* FROM chunk s JOIN hist h
            ON s.factUniqueId = h.factUniqueId AND h.__END_AT IS NULL AND h.contentHash <> s.contentHash
          UNION ALL SELECT s.factUniqueId AS mergeKey, s.* FROM chunk s) s
        ON t.factUniqueId = s.mergeKey AND t.__END_AT IS NULL
        WHEN MATCHED AND t.contentHash <> s.contentHash THEN UPDATE SET __END_AT = current_timestamp()
        WHEN NOT MATCHED THEN INSERT (factUniqueId, name, amount, occurredAt, contentHash, __START_AT, __END_AT)
          VALUES (s.factUniqueId, s.name, s.amount, s.occurredAt, s.contentHash, current_timestamp(), NULL)""")
    m = spark.sql(f"DESCRIBE HISTORY delta.`{HIST}` LIMIT 1").select("operationMetrics").first()[0]
    keys = ["numTargetRowsInserted", "numTargetRowsUpdated", "numTargetRowsCopied", "numDeletionVectorsAdded"]
    return {k: m.get(k) for k in keys}


@step("MERGE scd2 (100 changed, 50 new, 850 unchanged)")
def _():
    rows = [(f"k{i}", f"n{i}", float(i) + 0.5, "2026-10-01 10:00:00") for i in range(100)]
    rows += [(f"k{i}", f"n{i}", float(i), f"2026-10-0{1 + i % 9} 10:00:00") for i in range(100, 950)]
    rows += [(f"k{i}", f"n{i}", float(i), "2026-10-09 10:00:00") for i in range(1000, 1050)]
    m = scd2_merge(batch(rows))
    h = spark.read.format("delta").load(HIST)
    cur, closed = h.filter("__END_AT IS NULL").count(), h.filter("__END_AT IS NOT NULL").count()
    assert cur == 1050 and closed == 100, (cur, closed)
    return f"current={cur} closed={closed} metrics={m}"


@step("change data feed read (batch)")
def _():
    v = spark.sql(f"DESCRIBE HISTORY delta.`{HIST}` LIMIT 1").first()["version"]
    cdf = (spark.read.format("delta").option("readChangeFeed", "true").option("startingVersion", v).load(HIST)
           .groupBy("_change_type").count().collect())
    return {r[0]: r[1] for r in cdf}


@step("OPTIMIZE (liquid clustering)")
def _():
    r = spark.sql(f"OPTIMIZE delta.`{HIST}`").select("metrics.numFilesAdded", "metrics.numFilesRemoved").first()
    return f"added={r[0]} removed={r[1]}"


@step("VACUUM RETAIN 0 HOURS")
def _():
    n = spark.sql(f"VACUUM delta.`{HIST}` RETAIN 0 HOURS").count()
    return f"rows={spark.read.format('delta').load(HIST).count()} vacuumResultRows={n}"


@step("MERGE after OPTIMIZE leaves deletion vectors (for S2)")
def _():
    rows = [(f"k{i}", f"n{i}", float(i) + 0.75, "2026-10-02 10:00:00") for i in range(200, 220)]
    m = scd2_merge(batch(rows))
    files = spark.sql(f"DESCRIBE DETAIL delta.`{HIST}`").first()["numFiles"]
    return f"metrics={m} files={files}"


@step("DELETE writes a deletion vector")
def _():
    spark.sql(f"DELETE FROM delta.`{HIST}` WHERE factUniqueId = 'k999' AND __END_AT IS NULL")
    m = spark.sql(f"DESCRIBE HISTORY delta.`{HIST}` LIMIT 1").select("operationMetrics").first()[0]
    return {k: m.get(k) for k in ["numDeletionVectorsAdded", "numDeletedRows", "numCopiedRows"]}


def bronze_rows(start, n):
    return (spark.range(start, start + n).withColumn("payload", F.concat(F.lit("p"), F.col("id").cast("string")))
            .withColumn("_ingested_at", F.current_timestamp()))


@step("streaming read: Trigger.AvailableNow + s3a checkpoint, rerun reads nothing, append reads only new")
def _():
    bronze_rows(0, 500).write.format("delta").mode("overwrite").save(BRONZE)
    out, ck = f"{ROOT}/silver", f"{ROOT}/_checkpoints/silver"

    def drain():
        q = (spark.readStream.format("delta").load(BRONZE).writeStream.format("delta")
             .option("checkpointLocation", ck).trigger(availableNow=True).start(out))
        q.awaitTermination()
        p = q.lastProgress
        return (p or {}).get("numInputRows", 0)

    a = drain()
    b = drain()
    bronze_rows(500, 100).write.format("delta").mode("append").save(BRONZE)
    c = drain()
    total = spark.read.format("delta").load(out).count()
    assert (a, b, c, total) == (500, 0, 100, 600), (a, b, c, total)
    return f"first={a} rerun={b} afterAppend={c} total={total}"


@step("change feed streaming read with checkpoint")
def _():
    ck = f"{ROOT}/_checkpoints/cdf"
    seen = []

    def sink(df, bid):
        seen.append(df.filter("_change_type IN ('insert','update_postimage')").count())

    q = (spark.readStream.format("delta").option("readChangeFeed", "true").load(HIST).writeStream
         .foreachBatch(sink).option("checkpointLocation", ck).trigger(availableNow=True).start())
    q.awaitTermination()
    q = (spark.readStream.format("delta").option("readChangeFeed", "true").load(HIST).writeStream
         .foreachBatch(sink).option("checkpointLocation", ck).trigger(availableNow=True).start())
    q.awaitTermination()
    return f"batchesSeen={seen}"


@step("foreachBatch idempotent write: txnAppId/txnVersion, batch replayed after lost commit")
def _():
    src, out, ck = f"{ROOT}/fb_src", f"{ROOT}/fb_out", f"{ROOT}/_checkpoints/fb"
    bronze_rows(0, 300).write.format("delta").mode("overwrite").save(src)

    def sink(df, bid):
        (df.write.format("delta").mode("append").option("txnAppId", "s1-fb").option("txnVersion", bid).save(out))

    def run():
        q = (spark.readStream.format("delta").load(src).writeStream.foreachBatch(sink)
             .option("checkpointLocation", ck).trigger(availableNow=True).start())
        q.awaitTermination()

    run()
    first = spark.read.format("delta").load(out).count()
    jvm = spark.sparkContext._jvm
    path = jvm.org.apache.hadoop.fs.Path(f"{ck}/commits/0")
    fs = path.getFileSystem(spark.sparkContext._jsc.hadoopConfiguration())
    removed = fs.delete(path, False)
    run()
    second = spark.read.format("delta").load(out).count()
    direct = []
    for _ in range(2):
        bronze_rows(1000, 10).write.format("delta").mode("append").option("txnAppId", "s1-direct") \
            .option("txnVersion", 7).save(out)
        direct.append(spark.read.format("delta").load(out).count())
    assert first == 300 and second == 300 and direct == [310, 310], (first, second, direct)
    return f"first={first} commitRemoved={removed} afterReplay={second} directTwice={direct}"


@step("load full taxi month into Delta on RustFS")
def _():
    raw = spark.read.parquet("/data/taxi/yellow_tripdata_2024-01.parquet")
    (raw.write.format("delta").mode("overwrite")
     .option("delta.enableChangeDataFeed", "true").save(f"{ROOT}/taxi_raw"))
    t = spark.read.format("delta").load(f"{ROOT}/taxi_raw")
    types = {f.name: f.dataType.simpleString() for f in t.schema.fields if "time" in f.name}
    return f"rows={t.count()} timestampTypes={types}"


@step("taxi history-shaped table: timestamp_ntz, CDF, DV, CLUSTER BY, __START_AT/__END_AT")
def _():
    t = spark.read.format("delta").load(f"{ROOT}/taxi_raw")
    shaped = t.select(
        F.col("tpep_pickup_datetime").cast("timestamp_ntz").alias("pickupTime"),
        F.col("tpep_dropoff_datetime").cast("timestamp_ntz").alias("dropoffTime"),
        F.col("PULocationID").cast("int").alias("pickupLocationId"),
        F.col("DOLocationID").cast("int").alias("dropoffLocationId"),
        F.col("passenger_count").cast("int").alias("passengerCount"),
        F.col("trip_distance").cast("double").alias("tripDistance"),
        F.col("fare_amount").cast("double").alias("fareAmount"),
        F.col("total_amount").cast("double").alias("totalAmount"),
    )
    shaped = (shaped.withColumn("factUniqueId", F.sha2(F.concat_ws("\u001f", "pickupTime", "dropoffTime",
                                                                   "pickupLocationId", "fareAmount"), 256))
              .withColumn("__START_AT", F.current_timestamp())
              .withColumn("__END_AT", F.lit(None).cast("timestamp")))
    path = f"{ROOT}/taxi_history"
    shaped.createOrReplaceTempView("shaped")
    spark.sql(f"""CREATE OR REPLACE TABLE delta.`{path}` USING DELTA CLUSTER BY (factUniqueId)
                  TBLPROPERTIES ('delta.enableChangeDataFeed'='true', 'delta.enableDeletionVectors'='true')
                  AS SELECT * FROM shaped""")
    spark.sql(f"""UPDATE delta.`{path}` SET __END_AT = current_timestamp()
                  WHERE pickupLocationId = 132 AND hour(pickupTime) = 3""")
    m = spark.sql(f"DESCRIBE HISTORY delta.`{path}` LIMIT 1").select("operationMetrics").first()[0]
    n = spark.read.format("delta").load(path).count()
    return {"rows": n, "numUpdatedRows": m.get("numUpdatedRows"), "dv": m.get("numDeletionVectorsAdded")}


spark.stop()
with open("/work/s1-results.json", "w") as f:
    json.dump(RESULTS, f, indent=1)
print("S1DONE", sum(r["ok"] for r in RESULTS), "/", len(RESULTS))
sys.exit(0)
