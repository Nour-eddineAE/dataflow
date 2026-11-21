package com.dataflow.engine.transform;

import com.dataflow.engine.core.transform.TransformationCatalog;
import com.dataflow.spec.compile.CompiledTransformations;
import com.dataflow.transformation.Lake;
import com.dataflow.transformation.Transformation;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Set;

public class SpringTransformationCatalog implements TransformationCatalog, AutoCloseable {

    private final ApplicationContext parent;
    private final CompiledTransformations compiled;
    private GenericApplicationContext context;
    private Lake lake;

    public SpringTransformationCatalog(ApplicationContext parent, CompiledTransformations compiled) {
        this.parent = parent;
        this.compiled = compiled;
    }

    @Override
    public Set<String> ids() {
        return compiled.byId().keySet();
    }

    @Override
    public synchronized Transformation<?, ?> get(String id, Lake runLake) {
        if (context == null) {
            lake = runLake;
            context = new GenericApplicationContext();
            context.setParent(parent);
            context.setClassLoader(compiled.loader());
            context.registerBean(Lake.class, () -> lake);
            compiled.byId().forEach((name, type) -> context.registerBean(name, type.asSubclass(Transformation.class)));
            context.refresh();
        } else if (lake != runLake) {
            throw new IllegalStateException("One catalog serves one run");
        }
        return context.getBean(id, Transformation.class);
    }

    @Override
    public synchronized void close() {
        if (context != null) {
            context.close();
        }
    }
}
