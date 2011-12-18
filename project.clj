(defproject rplevy/clj-apache-https "2.0.0"
  :description "Clojure HTTP library using the Apache HttpClient. Based on clj-apache-http, but includes support for SSL client certificates and HttpAsyncClient."

  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.apache.httpcomponents/httpcore "4.1.1"]
                 [org.apache.httpcomponents/httpmime "4.1.1"]
                 [commons-logging/commons-logging "1.1.1"]
                 [org.apache.httpcomponents/httpclient "4.1.1"]
                 [org.apache.httpcomponents/httpasyncclient "4.0-alpha2"]
                 [commons-codec "1.5"]]
  :main com.twinql.clojure.core
  #_:repositories #_{"releases" ~(str "file://"
                                  user/local-maven-clone "/releases")
                 "snapshots" ~(str "file://"
                                   user/local-maven-clone "/snapshots")
                 "hotelicopter_snapshots" "https://raw.github.com/g1nn13/maven/master/snapshots"
                 "hotelicopter_releases" "https://raw.github.com/g1nn13/maven/master/releases"})