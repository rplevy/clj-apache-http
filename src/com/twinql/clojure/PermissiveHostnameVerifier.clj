;; Compile with:
;;
;; lein compile com.twinql.clojure.PermissiveHostnameVerifier
;;
;; Use this verifier to bypass SSL hostname verification.
;;
(ns com.twinql.clojure.PermissiveHostnameVerifier
  (:import
   (java.security.cert
    X509Certificate)
   (javax.net.ssl
    HostnameVerifier
    SSLSession
    SSLSocket)
   (org.apache.http.conn.ssl
    X509HostnameVerifier))
  (:gen-class
   :name com.twinql.clojure.PermissiveHostnameVerifier
   :implements [org.apache.http.conn.ssl.X509HostnameVerifier]
   :constructors {[] []}))

(defn -verify
  "Always returns null"
  [this host socket]
  (println "Called first version")
  nil)

(defn -verify
  "Always returns null"
  [this host cns subjectAlts]
  (println "Called second version")
  nil)

(defn -verify
  "Always returns null"
  [this host cert]
  (println "Called third version")
  nil)
