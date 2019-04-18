# Ichnaie

<!-- toc -->

  * [Overview](#overview)
  * [Clojure tools](#clojure-tools)
    + [`*tracing-enabled*`](#tracing-enabled)
    + [`enable-tracing!`](#enable-tracing)
    + [`disable-tracing!`](#disable-tracing)
    + [`set-global-tracer!`](#set-global-tracer)
    + [`with-tracer`](#with-tracer)
    + [`tracing`](#tracing)
    + [`tracing-with-spanbuilder`](#tracing-with-spanbuilder)
    + [`tracing-with-span`](#tracing-with-span)
  * [Thread Boundaries](#thread-boundaries)
  * [morphe integration](#morphe-integration)
  * [Java tool](#java-tool)
- [Maintainers and Contributors](#maintainers-and-contributors)
  * [Active Maintainers](#active-maintainers)
  * [Previous Contributors](#previous-contributors)

<!-- tocstop -->

## Overview

This repo contains a handful of Clojure utilities for easing project integration with the OpenTracing API. In addition, it contains a simple Java utility for Java applications that wrap a Clojure library employing `ichnaie`. The Java utility could probably be used on its own, but it was designed to enable a Java application to pass tracing information along to a Clojure library with little headache. Finally, we provide a function for integrating tracing into your project via morphe, for a true minimum of fuss and nonsense in Clojure.

The primary dependencies of this library are [Clojure](https://clojure.org/) (1.9.0-alpha-17) and [OpenTracing API](https://github.com/opentracing/opentracing-java/releases/tag/release-0.16.0) (0.16.0). It also pulls in [utiliva](https://github.com/Workiva/utiliva), [recide](https://github.com/Workiva/recide), and [potemkin](https://github.com/ztellman/potemkin).

This library maintains two thread-local stacks (via Clojure's [dynamic var](https://clojure.org/reference/vars)), one for the current tracer and the other for the current span. Pushing to and popping from the stack is accomplished with [`binding`](https://clojuredocs.org/clojure.core/binding).

## Clojure tools

### `*tracing-enabled*`

Dynamic variable that can be bound for local toggling of tracing behavior.

### `enable-tracing!`

Alters the var root of `*tracing-enabled*` to make the default be `true`.

### `disable-tracing!`

Alters the var root of `*tracing-enabled*` to make the default be false.

### `set-global-tracer!`

```clojure
;; [tracer]
(set-global-tracer! my-tracer)
```

By default, the tracer stack is empty; that is, there is no default tracer. This method allows you to specify a default tracer for *all users of ichnaie*, visible across all threads. `my-tracer` must be either a [Tracer](https://github.com/opentracing/opentracing-java/blob/423096a79aa7d1754629b40aee6f236e77ac06da/opentracing-api/src/main/java/io/opentracing/Tracer.java) or `nil`.

### `with-tracer`

```clojure
;; [tracer & body]
(with-tracer my-tracer
   (do some stuff)
   (maybe something else too))
```

`with-tracer` pushes the provided tracer onto the thread-local tracer stack, executes the body of code, then pops the tracer back off of the stack (even if an exception is thrown). The provided boy is executed in an implicit `do`.

### `tracing`

```clojure
;; [operation-name & body]
(tracing "adding-important-vals"
   (+ 42 Math/E 3))
```

This checks for a currently active span; if one exists, it creates a new child span; otherwise, it creates a span with no parent. In either case, the new span is pushed onto the thread-local stack. The span will be popped off the stack and the span's [`finish`](https://github.com/opentracing/opentracing-java/blob/423096a79aa7d1754629b40aee6f236e77ac06da/opentracing-api/src/main/java/io/opentracing/Span.java#L41) method will be called as this code block is exited, even if an exception is thrown. `tracing` checks for an active tracer; if none exists, an exception will be thrown and no span will be created.

### `tracing-with-spanbuilder`

```clojure
;; [builder & body]
(tracing-with-spanbuilder (.buildSpan my-tracer "some operation")
   (do stuff!)
   (again!))
```

This works like `tracing`, but the provided [SpanBuilder](https://github.com/opentracing/opentracing-java/blob/423096a79aa7d1754629b40aee6f236e77ac06da/opentracing-api/src/main/java/io/opentracing/Tracer.java#L92) is used to create the next span on the stack. As with `tracing`, the new span will be the child of any currently active span. If no tracer is on the stack, an exception is thrown instead.

### `tracing-with-span`

```clojure
;; [span & body]
(tracing-with-span my-handcrafted-span
   (busy ... busy ... busy))
```

Once again, this works like the `tracing` macro, except you are providing your own span: no new span is built, and this span will not be marked as the child of any current span (unless you do so explicitly). All this macro does is push the span onto the stack and ensure that it is popped off and `finish`ed at the end.

## Thread Boundaries

The dynamic vars used by `ichnaie` are registered with [`plasmodesma`](https://github.com/Workiva/utiliva/blob/master/src/utiliva/plasmodesma.clj), so you can use its `future`, `pmap` etc. with impunity.

## morphe integration

We provide an aspect, `traced`, which can be registered with [`morphe`](https://github.com/Workiva/morphe) to provide pain-free tracing integration. All that is necessary to trace a particular function is to add it to the aspects metadata:

```clojure
(ns my-ns
  (:require [morphe.core :as m]))
(m/defn ^{::m/aspects [traced]} traced-fn [x y & zs] (blah x y z) ...)
```

Spans created when this function is called will have the operation name `my-ns/traced-fn:[x y & zs]`. When your function has multiple arities, the span will display the correct name for the arity that was called.

## Java tool

This library provides a [ThreadContext](java-src/ichnaie/TracingContext.java) class that behaves similarly to the Clojure macros above. It implements [AutoCloseable](https://docs.oracle.com/javase/8/docs/api/java/lang/AutoCloseable.html), as it is intended to be used primarily (exclusively?) in the context of Java's [try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html) construction:

```java
try(TracingContext tc = new TracingContext(myTracer) {
   try(TracingContext tc = new TracingContext("my operation name")) {
      // This pushes and starts the new span with that operation name.
      // Like the Clojure macros, it creates child spans where appropriate.
      // When the try body is complete, the span is finished and popped from the stack.
   }
   try(TracingContext tc = new TracingContext(myBuilder) {
      // Similar, but makes a new span with this specific builder.
   }
   try(TracingContext tc = new TracingContext(specificSpan) {
      // Or maybe you just want to provide your own span.
   }
}

try(TracingContext tc = new TracingContext(myTracer, myBuilder)) {
   // You don't have to specify the tracer separately
}
```

The `TracingContext` can be created with a Tracer, a SpanBuilder, or a Span; or with a combination of Tracer and String, Tracer and SpanBuilder, or Tracer and Span.

As with the Clojure macros, an exception is thrown when an attempt is made to create a span in a context that has no tracer defined. `TracingContext.setGlobalTracer(Tracer tracer)` is provided as a static method which simply delegates to the Clojure version.

# Maintainers and Contributors

## Active Maintainers

-

## Previous Contributors

- Timothy Dean <galdre@gmail.com>
- Alex Alegre <alex.alegre@workiva.com>
- Aleksandr Furmanov <aleksandr.furmanov@workiva.com>
- Tyler Wilding <tyler.wilding@workiva.com>
