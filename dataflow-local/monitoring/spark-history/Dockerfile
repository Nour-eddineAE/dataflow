ARG SPARK_VERSION

FROM maven:3.9.15-eclipse-temurin-21 AS jars
ARG HADOOP_VERSION
ARG AWS_SDK_VERSION
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:copy-dependencies \
      -Dhadoop.version=${HADOOP_VERSION} -Daws-sdk.version=${AWS_SDK_VERSION}

FROM apache/spark:${SPARK_VERSION}-scala2.13-java21-ubuntu
COPY --from=jars /build/target/jars/ /opt/spark/jars/
