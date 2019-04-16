// Copyright 2017-2019 Workiva Inc.
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
//     http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package ichnaie;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Var;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;

public class TracingContext implements AutoCloseable {
    private Tracer tracer;
    private Span span;

    static {
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("ichnaie.core"));
        Var tracerVar = (Var) Clojure.var("ichnaie.core", "*tracer*");
        assert tracerVar.hasRoot();
        assert tracerVar.isDynamic();
        Var stackVar = (Var) Clojure.var("ichnaie.core","*trace-stack*");
        assert stackVar.hasRoot();
        assert stackVar.isDynamic();
    }

    private static Var getVar(String ns, String sym) {
        return (Var) Clojure.var(ns, sym);
    }

    private static Object getVarValue(Var v) {
        if (v.hasRoot()) {
            return v.deref();
        } else {
            return null;
        }
    }

    public TracingContext(String operationName) {
        Var tracerVar = getVar("ichnaie.core","*tracer*");
        Tracer tracer = (Tracer) getVarValue(tracerVar);
        if (tracer == null) {
            throw new NullPointerException("No Tracer set.");
        }
        Var stackVar = getVar("ichnaie.core","*trace-stack*");
        Span parent = (Span) getVarValue(stackVar);
        Span span;
        if (parent == null) {
            span = tracer.buildSpan(operationName).start();
        } else {
            span = tracer.buildSpan(operationName).asChildOf(parent).start();
        }
        this.span = span;
        IFn hashMap = Clojure.var("clojure.core","hash-map");
        Var.pushThreadBindings((clojure.lang.Associative) hashMap.invoke(stackVar, span));
    }

    public TracingContext(Span newSpan) {
        Var tracerVar = getVar("ichnaie.core","*tracer*");
        Tracer tracer = (Tracer) getVarValue(tracerVar);
        if (tracer == null) {
            throw new NullPointerException("No Tracer set.");
        }
        this.span = newSpan;
        Var stackVar = getVar("ichnaie.core","*trace-stack*");
        IFn hashMap = Clojure.var("clojure.core","hash-map");
        Var.pushThreadBindings((clojure.lang.Associative) hashMap.invoke(stackVar, newSpan));
    }

    public TracingContext(SpanBuilder builder) {
        Var tracerVar = getVar("ichnaie.core","*tracer*");
        Tracer tracer = (Tracer) getVarValue(tracerVar);
        if (tracer == null) {
            throw new NullPointerException("No Tracer set.");
        }
        Var stackVar = getVar("ichnaie.core","*trace-stack*");
        Span parent = (Span) getVarValue(stackVar);
        Span span;
        if (parent == null) {
            span = builder.start();
        } else {
            span = builder.asChildOf(parent).start();
        }
        this.span = span;
        IFn hashMap = Clojure.var("clojure.core","hash-map");
        Var.pushThreadBindings((clojure.lang.Associative) hashMap.invoke(stackVar, span));
    }

    public TracingContext(Tracer tracer) {
        Var tracerVar = getVar("ichnaie.core","*tracer*");
        IFn hashMap = Clojure.var("clojure.core","hash-map");
        this.tracer = tracer;
        Var.pushThreadBindings((clojure.lang.Associative) hashMap.invoke(tracerVar, tracer));
    }

    public TracingContext(Tracer tracer, String operationName) {
        Var tracerVar = getVar("ichnaie.core", "*tracer*");
        Var stackVar = getVar("ichnaie.core", "*trace-stack*");
        Span parent = (Span) getVarValue(stackVar);
        Span span;
        if (parent == null) {
            span = tracer.buildSpan(operationName).start();
        } else {
            span = tracer.buildSpan(operationName).asChildOf(parent).start();
        }
        this.tracer = tracer;
        this.span = span;
        IFn hashMap = Clojure.var("clojure.core", "hash-map");
        Var.pushThreadBindings((clojure.lang.Associative) hashMap.invoke(tracerVar, tracer, stackVar, span));
    }

    public TracingContext(Tracer tracer, Span newSpan) {
        Var tracerVar = getVar("ichnaie.core", "*tracer*");
        Var stackVar = getVar("ichnaie.core", "*trace-stack*");
        this.tracer = tracer;
        this.span = newSpan;
        IFn hashMap = Clojure.var("clojure.core", "hash-map");
        Var.pushThreadBindings((clojure.lang.Associative) hashMap.invoke(tracerVar, tracer, stackVar, newSpan));
    }

    public TracingContext(Tracer tracer, SpanBuilder builder) {
        Var tracerVar = getVar("ichnaie.core", "*tracer*");
        Var stackVar = getVar("ichnaie.core", "*trace-stack*");
        Span parent = (Span) getVarValue(stackVar);
        Span span;
        if (parent == null) {
            span = builder.start();
        } else {
            span = builder.asChildOf(parent).start();
        }
        this.tracer = tracer;
        this.span = span;
        IFn hashMap = Clojure.var("clojure.core", "hash-map");
        Var.pushThreadBindings((clojure.lang.Associative) hashMap.invoke(tracerVar, tracer, stackVar, span));
    }

    @Override
    public void close() {
        if (tracer != null &&
            tracer != (Tracer) ((Var) Clojure.var("ichnaie.core", "*tracer*")).deref()) {
            throw new IllegalStateException("Unexpected Tracer set.");
        }
        if (span != null &&
            span != (Span) ((Var) Clojure.var("ichnaie.core","*trace-stack*")).deref()) {
            throw new IllegalStateException("Unexpected Span found.");
        }
        if (span != null) {
            span.finish();
        }
        Var.popThreadBindings();
    }

    public static void setGlobalTracer(Tracer tracer) {
        IFn setGlobalTracerBang = Clojure.var("ichnaie.core", "set-global-tracer!");
        setGlobalTracerBang.invoke(tracer);
    }
}
