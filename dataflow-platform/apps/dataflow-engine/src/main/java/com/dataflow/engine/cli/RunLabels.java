package com.dataflow.engine.cli;

import com.dataflow.engine.core.run.EngineRuntime;
import com.dataflow.engine.release.ActiveRelease;
import com.dataflow.spec.settings.RecordedSetting;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
class RunLabels {

    private static final String SPARK_APP = "spark_app";
    private static final String EVENT_LOG = "spark.eventLog.enabled";

    private final ActiveRelease release;
    private final ObjectProvider<BuildProperties> build;
    private final ObjectProvider<SparkSession> spark;
    private final EngineSettings settings;

    Map<String, Object> labels() {
        SparkSession session = spark.getObject();
        Map<String, Object> labels = new HashMap<>(release.labels());
        BuildProperties info = build.getIfAvailable();
        labels.putAll(EngineRuntime.of(info == null ? null : info.getVersion(), session.version()).labels());
        if (Boolean.parseBoolean(session.conf().get(EVENT_LOG, Boolean.FALSE.toString()))) {
            labels.put(SPARK_APP, session.sparkContext().applicationId());
        }
        labels.put(RecordedSetting.RUN_DETAIL, settings.recorded());
        return labels;
    }
}
