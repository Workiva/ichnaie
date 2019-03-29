(defproject com.workiva/ichnaie "0.1.0"
  :description "A handful of Clojure utilities for easing project integration with the OpenTracing API"
  :url "https://github.com/Workiva/ichnaie"
  :license {:name "Apache License, Version 2.0"}
  :plugins [[lein-shell "0.5.0"]
            [lein-codox "0.10.3"]]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.workiva/utiliva "0.1.1"]
                 [com.workiva/recide "1.0.0"]
                 [io.opentracing/opentracing-api "0.31.0"]
                 [com.workiva/morphe "1.0.0"]
                 [potemkin "0.4.3"]]

  :deploy-repositories {"clojars"
                        {:url "https://repo.clojars.org"
                         :sign-releases false}}

  :java-source-paths ["java-src"]
  :source-paths      ["src"]
  :test-paths        ["test"]

  :global-vars {*warn-on-reflection* true}

  :aliases {"docs" ["do" "clean-docs," "codox"]
            "clean-docs" ["shell" "rm" "-rf" "./documentation"]}

  :codox {:output-path "documentation"}

  :profiles {:dev [{:dependencies [[criterium "0.4.3"]]}]})
