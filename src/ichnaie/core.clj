;; Copyright 2017-2019 Workiva Inc.
;; 
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;; 
;;     http://www.apache.org/licenses/LICENSE-2.0
;; 
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns ichnaie.core
  (:require [recide.core :refer [insist]]
            [clojure.tools.logging :refer [warnf]])
  (:import [io.opentracing Tracer Span Tracer$SpanBuilder]))

(def ^:private warning-flag (atom true))
(def ^:private warning-sites (atom #{}))

(def ^:dynamic *warn-always* false)
(def ^:dynamic *trace-stack* nil)
(def ^:dynamic *tracer* nil)
(def ^:dynamic *tracing-enabled* true)

(defn set-global-tracer!
  "Alters (mutates) the root of the *tracer* dynamic var to refer
  to the supplied tracer. Complains if tracer is not either a Tracer or null."
  [tracer]
  (insist (or (nil? tracer) (instance? Tracer tracer)))
  (reset! warning-flag true)
  (reset! warning-sites #{})
  (alter-var-root #'*tracer* (constantly tracer)))

(defn- set-tracing [b] (alter-var-root #'*tracing-enabled* (constantly b)))
(defn enable-tracing! "Alters the root of *tracing-enabled* to true" [] (set-tracing true))
(defn disable-tracing! "Alters the root of *tracing-enabled* to false" [] (set-tracing false))

(defn- start-span-code
  [span-form]
  `(as-> ~span-form ^Tracer$SpanBuilder span#
     (if-let [parent# *trace-stack*]
       (.asChildOf span# ^Span parent#)
       span#)
     (.start span#)))

(defn- trace-impl
  [span-sym body-fn]
  `(binding [*trace-stack* ~span-sym]
     (try (~body-fn)
          (finally (.finish ~(with-meta span-sym
                               {:tag `Span}))))))

(defn- warn-no-tracer
  [line]
  (when (or @warning-flag ;; Ordered least-to-most expensive
            (and *warn-always*
                 (not (contains? warning-sites [*ns* line]))))
    (reset! warning-flag false)
    (swap! warning-sites conj [*ns* line])
    `(warnf "Tracing is enabled, but no tracer is set. %s - line %s" *ns* ~line)))

(defmacro with-tracer
  "When tracing is enabled, this binds the global tracer var to the supplied tracer and
  executes the body in that context."
  [tracer & body]
  (let [{:keys [line]} (meta &form)]
    `(let [f# (fn [] ~@body)]
       (if *tracing-enabled*
         (binding [*tracer* ~tracer] (f#))
         (f#)))))

(defmacro tracing
  "When tracing is enabled, this creates a span with the given operation name (as the child of any
  previous span on the stack), pushes it onto the span stack, and executes the body in the context
  of that span. Completes the span and pops it from the stack on exit."
  [operation-name & body]
  (let [op-name (gensym "op-name")
        span-sym (gensym "span")
        body-fn (gensym 'body)
        {:keys [line]} (meta &form)]
    `(let [~body-fn (fn [] ~@body)]
       (cond (and *tracing-enabled* *tracer*)
             (let [~op-name ~operation-name
                   ~'_ (insist (string? ~op-name) "The operation-name argument to the tracing macro should be a string.")
                   ~'_ (insist (some? *tracer*) "There is no tracer!")
                   ~span-sym ~(start-span-code `(.buildSpan ^Tracer *tracer* ~op-name))]
               ~(trace-impl span-sym body-fn))
             *tracing-enabled*
             (do ~(warn-no-tracer line) (~body-fn))
             :else
             (~body-fn)))))

(defmacro tracing-with-spanbuilder
  "When tracing is enabled, this specifies a spanbuilder which is used to create a span
  and push it onto the span stack; then the body is executed in the context of that span.
  Completes the span and pops it from the stack on exit."
  [builder & body]
  (let [span-sym (gensym "builder")
        body-fn (gensym 'body)
        {:keys [line]} (meta &form)]
    `(let [~body-fn (fn [] ~@body)]
       (cond (and *tracing-enabled* *tracer*)
             (let [~span-sym ~(start-span-code builder)]
               ~(trace-impl span-sym body-fn))
             *tracing-enabled*
             (do ~(warn-no-tracer line) (~body-fn))
             :else
             (~body-fn)))))

(defmacro tracing-with-span
  "When tracing is enabled, this pushes the provided span onto the span stack; then the body
  is executed in the context of that span. Completes the span and pops it from the stack on exit."
  [span & body]
  (let [body-fn (gensym 'body)
        {:keys [line]} (meta &form)]
    `(let [~body-fn (fn [] ~@body)]
       (cond (and *tracing-enabled* *tracer*)
             ~(trace-impl span body-fn)
             *tracing-enabled*
             (do ~(warn-no-tracer line) (~body-fn))
             :else
             (~body-fn)))))

(defn traced
  "This function is designed for use with the morphe library. When triggered, this wraps each
  fn body with a `tracing` macro using an operation name of the form:
  <namespace>/<fn-name>:<params>
  For instance:
  \"clojure.core/+:[x y & more]\""
  [fn-form]
  (insist (contains? fn-form :arglists)
          "It appears you may be using an older version of morphe. See mod:traced.")
  (let [trace-str-prefix (format "%s/%s" (ns-name (:namespace fn-form)) (:fn-name fn-form))]
    (update fn-form :bodies
            (fn [bodies]
              (for [[args body] (map vector (:arglists fn-form) bodies)]
                (let [trace-str (format "%s:%s" trace-str-prefix (pr-str args))]
                  `((tracing ~trace-str ~@body))))))))

(defn mod:traced
  "DEPRECATED. For use with older versions of morphe.
  This function is designed for use with the morphe library. When triggered,
  this wraps each fn body with a `tracing` macro using an operation name of the form:
  <namespace>/<fn-name>:<args-vector>
  For instance:
  \"clojure.core/+:[x y & more]\""
  [fn-form]
  (insist (contains? fn-form :argslists)
          "mod:traced is deprecated -- use `traced` with newer versions of morphe.")
  (let [trace-str-prefix (format "%s/%s" (ns-name (:namespace fn-form)) (:fn-name fn-form))]
    (update fn-form :bodies
            (fn [bodies]
              (for [[args body] (zipmap (:argslists fn-form) bodies)]
                (let [trace-str (format "%s:%s" trace-str-prefix (pr-str args))]
                  `((tracing ~trace-str ~@body))))))))
