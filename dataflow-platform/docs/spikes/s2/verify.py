import os
from pyspark.sql import SparkSession, functions as F

spark = (SparkSession.builder.appName("s2-verify")
         .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
         .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
         .config("spark.hadoop.fs.s3a.endpoint", "http://rustfs:9000")
         .config("spark.hadoop.fs.s3a.endpoint.region", "us-east-1")
         .config("spark.hadoop.fs.s3a.path.style.access", "true")
         .config("spark.hadoop.fs.s3a.access.key", os.environ["RUSTFS_ACCESS_KEY"])
         .config("spark.hadoop.fs.s3a.secret.key", os.environ["RUSTFS_SECRET_KEY"])
         .config("spark.hadoop.fs.s3a.buffer.dir", "/tmp")
         .config("spark.sql.shuffle.partitions", "4").config("spark.ui.enabled", "false").getOrCreate())
spark.sparkContext.setLogLevel("ERROR")
H = "s3a://lake/s1/history"
for v in (4, 5):
    print("S2V spark time travel", v, spark.read.format("delta").option("versionAsOf", v).load(H).count())
c = spark.read.format("delta").option("readChangeFeed", "true").option("startingVersion", 5).option("endingVersion",
                                                                                                    5).load(H)
print("S2V spark cdf v5", {r[0]: r[1] for r in c.groupBy("_change_type").count().collect()})
NO_DV = "s3a://lake/s1/history_nodv"
spark.read.format("delta").load(H).write.format("delta").mode("overwrite") \
    .option("delta.enableChangeDataFeed", "true").option("delta.enableDeletionVectors", "false").save(NO_DV)
spark.sql(f"DELETE FROM delta.`{NO_DV}` WHERE factUniqueId = 'k998' AND __END_AT IS NULL")
spark.sql(f"UPDATE delta.`{NO_DV}` SET __END_AT = current_timestamp() WHERE factUniqueId = 'k997' AND __END_AT IS NULL")
for r in spark.sql(f"DESCRIBE HISTORY delta.`{NO_DV}`").select("version", "operation").collect():
    print("S2V nodv history", r[0], r[1])
c = spark.read.format("delta").option("readChangeFeed", "true").option("startingVersion", 1).load(NO_DV)
print("S2V spark cdf nodv",
      sorted((r[0], r[1], r[2]) for r in c.groupBy("_change_type", "_commit_version").count().collect()))
rows = spark.createDataFrame([(f"live{i}", "live", float(i), None, "h", None, None) for i in range(10)],
                             "factUniqueId string, name string, amount double, occurredAt timestamp_ntz, contentHash string, __START_AT timestamp, __END_AT timestamp")
rows.write.format("delta").mode("append").save(H)
print("S2V appended 10 rows to history; spark count now", spark.read.format("delta").load(H).count())
spark.stop()
