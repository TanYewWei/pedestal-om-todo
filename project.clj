(defproject pedestal-om-todo "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[com.cemerick/piggieback "0.1.0"]
                 [ch.qos.logback/logback-classic "1.0.13" :exclusions [org.slf4j/slf4j-api]]                 
                 [cljs-uuid "0.0.4"] 
                 [domina "1.0.1"]
                 [io.pedestal/pedestal.app "0.2.2"]
                 [io.pedestal/pedestal.app-tools "0.2.2"]
                 [om "0.2.3"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [prismatic/schema "0.2.0"]
                 [sablono "0.2.1"]
                 [secretary "0.4.0"]]
  :min-lein-version "2.0.0"
  :source-paths ["app/src" "app/templates"]
  :resource-paths ["config"]
  :target-path "out/"
  :repl-options  {:init-ns user
                  :init (try
                          (use 'io.pedestal.app-tools.dev)
                          (catch Throwable t
                            (println "ERROR: There was a problem loading io.pedestal.app-tools.dev")
                            (clojure.stacktrace/print-stack-trace t)
                            (println)))
                  :welcome (println "Welcome to pedestal-app! Run (tools-help) to see a list of useful functions.")
                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :main ^{:skip-aot true} io.pedestal.app-tools.dev)
