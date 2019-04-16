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

(ns ichnaie.tracing-test
  (:require [ichnaie.core :as core]
            [utiliva.plasmodesma :as plasma]
            [morphe.core :as m]
            [clojure.test :refer :all])
  (:import [ichnaie TracingContext]
           [io.opentracing Tracer Span Tracer$SpanBuilder]))

(alter-var-root #'core/*tracing-enabled* (constantly true))

(defn- mock-span
  ([s]
   (reify Span
     (finish [this] nil)))
  ([s a]
   (reify Span
     (finish [this] (swap! a inc) nil))))

(defn- mock-builder
  ([s]
   (reify Tracer$SpanBuilder
     (^Tracer$SpanBuilder asChildOf [this ^Span span]
      this)
     (start [this]
       (mock-span s))))
  ([s a]
   (reify Tracer$SpanBuilder
     (^Tracer$SpanBuilder asChildOf [this ^Span span]
      this)
     (start [this]
       (mock-span s a)))))

(defn- mock-tracer
  ([]
   (reify Tracer
     (buildSpan [this s]
       (mock-builder s))))
  ([a]
   (reify Tracer
     (buildSpan [this s]
       (mock-builder s a)))))

(deftest test:tracer-root
  (try
    (is (nil? core/*tracer*))
    (is (thrown? Exception (core/set-global-tracer! 4)))
    (let [tracer (mock-tracer)]
      (core/set-global-tracer! tracer)
      (is (identical? core/*tracer* tracer)))
    (finally (core/set-global-tracer! nil))))

(def box (atom nil))
(deftest test:tracing
  (try
    (core/with-tracer (mock-tracer)
      (is (nil? core/*trace-stack*))
      (is (= 3 (core/tracing "second span" (+ 1 2))))
      (core/tracing "third span"
                    (reset! box core/*trace-stack*)
                    (core/tracing "fourth span"
                                  (is (not= core/*trace-stack* @box)))
                    (is (identical? core/*trace-stack* @box)))
      (is (nil? core/*trace-stack*)))
    (is (nil? core/*tracer*))
    (finally (core/set-global-tracer! nil))))

(deftest test:tracing-with-spanbuilder
  (try
    (core/with-tracer (mock-tracer)
      (is (nil? core/*trace-stack*))
      (is (thrown? Exception (core/tracing-with-spanbuilder "i am a string" (+ 1 2))))
      (let [builder (mock-builder "Span me!")]
        (core/tracing-with-spanbuilder builder
                                       (is (some? core/*trace-stack*))))
      (is (nil? core/*trace-stack*)))
    (is (nil? core/*tracer*))
    (finally (core/set-global-tracer! nil))))

(deftest test:tracing-with-span
  (try
    (core/with-tracer (mock-tracer)
      (is (nil? core/*trace-stack*))
      (is (thrown? Exception (core/tracing-with-span (mock-builder "I'm a builder") (+ 1 2))))
      (let [span (mock-span "Span me!")]
        (core/tracing-with-span span
                                (is (identical? span core/*trace-stack*))))
      (is (nil? core/*trace-stack*)))
    (is (nil? core/*tracer*))
    (finally (core/set-global-tracer! nil))))

(deftest test:tracing-context
  (testing "basic functionality"
    (try (is (nil? core/*tracer*))
         (let [finish-count (atom 0)
               tc (TracingContext. ^Tracer (mock-tracer finish-count))]
           (is (some? core/*tracer*))
           (is (zero? @finish-count))
           (is (nil? core/*trace-stack*))
           (let [tc2 (TracingContext. "some operation")]
             (is (some? core/*trace-stack*))
             (is (zero? @finish-count))
             (.close tc2)
             (is (= 1 @finish-count)))
           (is (nil? core/*trace-stack*))
           (let [span (mock-span "another operation" finish-count)
                 tc3 (TracingContext. ^Span span)]
             (is (identical? span core/*trace-stack*))
             (.close tc3)
             (is (= 2 @finish-count)))
           (is (nil? core/*trace-stack*))
           (let [builder (mock-builder "and another!" finish-count)
                 tc4 (TracingContext. ^Tracer$SpanBuilder builder)]
             (is (some? core/*trace-stack*))
             (.close tc4)
             (is (= 3 @finish-count)))
           (.close tc))
         (is (nil? core/*tracer*))
         (finally (core/set-global-tracer! nil))))
  (testing "can't close out of order."
    ;; TODO: this doesn't stop all out-of-order closes
    (try (is (nil? core/*tracer*))
         (let [tc (TracingContext. ^Tracer (mock-tracer))]
           (let [tc2 (TracingContext. ^Tracer$SpanBuilder (mock-builder "a builder"))]
             (let [tc3 (TracingContext. ^Tracer$SpanBuilder (mock-builder "another"))]
               (is (thrown? Exception (.close tc2)))
               (.close tc3))
             (.close tc2))
           (.close tc))
         (let [tc (TracingContext. ^Tracer (mock-tracer) ^Span (mock-span "hi"))]
           (let [tc2 (TracingContext. ^Span (mock-span "bye"))]
             (is (thrown? Exception (.close tc)))
             (.close tc2))
           (.close tc))
         (is (nil? core/*tracer*)))))

(defn get-tracer [_] @#'core/*tracer*)

(deftest test:tracing-through-threads
  ;; TODO: investigate more fully clojure.core/future, clojure.core/pmap behavior regarding dynamic var bindings.
  (try
    (let [tracer (mock-tracer)]
      (core/with-tracer tracer
        (is (identical? tracer core/*tracer*))
        (is (identical? tracer @(plasma/future core/*tracer*)))
        (is (= 3 (core/tracing "second span" (+ 1 2))))
        (let [span (mock-span "Spanny")]
          (core/tracing-with-span span
                                  (is (identical? span core/*trace-stack*))
                                  (is (identical? span @(plasma/future core/*trace-stack*)))))
        (is (nil? core/*trace-stack*))))
    (finally (core/set-global-tracer! nil))))

(deftest morphe-integration
  (is
    (try
      (m/defn ^{::m/aspects [core/traced]} test [] nil)
      (catch Exception _
        false))))

