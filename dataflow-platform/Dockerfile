FROM node:22.17.0-alpine AS ui
WORKDIR /ui
COPY apps/dataflow-workspace/ui/package.json apps/dataflow-workspace/ui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY apps/dataflow-workspace/ui/ ./
RUN npm test && npm run build

FROM maven:3.9.15-eclipse-temurin-21 AS build
WORKDIR /build

COPY .mvn .mvn
COPY pom.xml .
COPY dataflow-bom/pom.xml dataflow-bom/
COPY libs/dataflow-config/pom.xml libs/dataflow-config/
COPY libs/dataflow-transformation-api/pom.xml libs/dataflow-transformation-api/
COPY libs/dataflow-spec/pom.xml libs/dataflow-spec/
COPY libs/dataflow-engine-core/pom.xml libs/dataflow-engine-core/
COPY apps/dataflow-engine/pom.xml apps/dataflow-engine/
COPY apps/dataflow-workspace/pom.xml apps/dataflow-workspace/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -fn -DskipTests dependency:go-offline

COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q -T 0.5C -DskipTests package

RUN java -Djarmode=tools -jar apps/dataflow-engine/target/dataflow-engine-*.jar extract \
        --destination /extract/engine --application-filename app.jar \
 && java -Djarmode=tools -jar apps/dataflow-workspace/target/dataflow-workspace-*.jar extract \
        --destination /extract/workspace --application-filename app.jar

RUN mkdir -p /owned/cache

RUN jlink --add-modules ALL-MODULE-PATH \
    --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
    --output /javaruntime

FROM gcr.io/distroless/base-debian12 AS runtime
WORKDIR /app
ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:$PATH \
    JDK_JAVA_OPTIONS="\
--add-opens=java.base/java.lang=ALL-UNNAMED \
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens=java.base/java.io=ALL-UNNAMED \
--add-opens=java.base/java.net=ALL-UNNAMED \
--add-opens=java.base/java.nio=ALL-UNNAMED \
--add-opens=java.base/java.util=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
--add-opens=java.base/sun.security.action=ALL-UNNAMED \
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
-Dspark.hadoop.fs.s3a.buffer.dir=/tmp \
-Dspark.sql.warehouse.dir=/tmp \
-XX:MaxRAMPercentage=50 \
-XX:+UseG1GC \
-XX:G1PeriodicGCInterval=60000 \
-XX:MinHeapFreeRatio=10 \
-XX:MaxHeapFreeRatio=30"
COPY --from=build /javaruntime /opt/java





FROM runtime AS engine
LABEL org.dataflow.component=engine
ENV JDK_JAVA_OPTIONS="${JDK_JAVA_OPTIONS} -XX:MaxRAMPercentage=60"
COPY --from=build /extract/engine /app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["validate"]

FROM runtime AS workspace
LABEL org.dataflow.component=workspace
COPY --from=build /extract/workspace /app
COPY --from=ui /ui/dist /app/ui
COPY --from=build --chown=65532:65532 /owned/cache /var/cache/dataflow
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM apache/spark:4.2.0 AS spark

FROM ubuntu:22.04 AS engine-k8s
LABEL org.dataflow.component=engine-k8s
RUN apt-get update \
 && apt-get install -y --no-install-recommends tini procps \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 185 spark \
 && useradd --system --uid 185 --gid 185 --home-dir /opt/spark --no-create-home spark \
 && mkdir -p /opt/spark/work-dir \
 && chown spark:spark /opt/spark/work-dir
COPY --from=build /javaruntime /opt/java
COPY --from=spark /opt/entrypoint.sh /opt/decom.sh /opt/
COPY --from=spark /opt/spark/bin /opt/spark/bin
COPY --from=build /extract/engine/lib/ /opt/spark/jars/
COPY --from=build /build/apps/dataflow-engine/target/dataflow-engine-*.jar.original /opt/dataflow/dataflow-engine.jar
ENV JAVA_HOME=/opt/java \
    SPARK_HOME=/opt/spark \
    PATH=/opt/java/bin:/opt/spark/bin:$PATH \
    SPRING_PROFILES_ACTIVE=k8s
WORKDIR /opt/spark/work-dir
ENTRYPOINT ["/opt/entrypoint.sh"]
USER 185
