(defproject com.workiva/ichnaie "0.1.0"
  :description "A handful of Clojure utilities for easing project integration with the OpenTracing API"
  :url "https://github.com/Workiva/ichnaie"
  :license {:name "Apache License, Version 2.0"}

  :plugins [[lein-shell "0.5.0"]
            [lein-cljfmt "0.6.4"]
            [lein-codox "0.10.3"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.workiva/utiliva "0.1.1"]
                 [com.workiva/recide "1.0.0"]
                 [io.opentracing/opentracing-api "0.31.0"]
                 [com.workiva/morphe "1.0.0"]
                 [potemkin "0.4.3"]]

  :deploy-repositories {"clojars"
                        {:url "https://repo.clojars.org"
                         :username :env/clojars_username
                         :password :env/clojars_password
                         :sign-releases false}}

  :java-source-paths ["java-src"]
  :source-paths      ["src"]
  :test-paths        ["test"]

  :global-vars {*warn-on-reflection* true}

  :aliases {"docs" ["do" "clean-docs," "with-profile" "docs" "codox," "java-docs"]
            "clean-docs" ["shell" "rm" "-rf" "./documentation"]
            "java-docs" ["shell" "javadoc" "-d" "./documentation/java" "-notimestamp"
                         "./java-src/ichnaie/TracingContext.java"]}

  :codox {:metadata {:doc/format :markdown}
          :themes [:rdash]
          :html {:transforms [[:title]
                              [:substitute [:title "Ichnaie API Docs"]]
                              [:span.project-version]
                              [:substitute nil]
                              [:pre.deps]
                              [:substitute [:a {:href "https://clojars.org/com.workiva/ichnaie"}
                                            [:img {:src "https://img.shields.io/clojars/v/com.workiva/ichnaie.svg"}]]]]}
          :output-path "documentation/clojure"}

  :profiles {:dev [{:dependencies [[criterium "0.4.3"]]}]
             :docs {:dependencies [[codox-theme-rdash "0.1.2"]]}})
