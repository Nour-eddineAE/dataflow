package com.dataflow.engine.core.transform;

import com.dataflow.transformation.Lake;
import com.dataflow.transformation.Transformation;

import java.util.Set;

public interface TransformationCatalog {

    Set<String> ids();

    Transformation<?, ?> get(String id, Lake lake);
}
