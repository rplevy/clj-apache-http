(ns com.twinql.clojure.async-client
  (:import (java.util.concurrent
            CountDownLatch
            TimeUnit))
  ;;(:import (java.io.InputStreamReader))
  (:import (javax.net.ssl
            SSLContext))
  (:import (org.apache.http.impl.nio.conn
            PoolingClientConnectionManager))
  (:import (org.apache.http
            HttpResponse
            HttpHost))
  (:import (org.apache.http.client.methods
            HttpRequestBase
            HttpDelete
            HttpGet
            HttpHead
            HttpOptions
            HttpPost
            HttpPut))
  (:import (org.apache.http.params
            BasicHttpParams))
  (:import (org.apache.http.conn.params
            ConnManagerPNames
            ConnPerRouteBean))
  (:import (org.apache.http.impl.nio.client
            DefaultHttpAsyncClient))
  (:import (org.apache.http.nio.client
            HttpAsyncClient))
  (:import (org.apache.http.nio.concurrent
            FutureCallback))
  (:import (org.apache.http.nio.conn.scheme
            Scheme
            SchemeRegistry))
  (:import (org.apache.http.nio.conn.ssl
            SSLLayeringStrategy))
  (:import (org.apache.http.conn.ssl
            X509HostnameVerifier
            AllowAllHostnameVerifier
            StrictHostnameVerifier))
  (:import (org.apache.http.nio.conn.ssl
            SSLLayeringStrategy))
  (:import (org.apache.http.impl.nio.reactor
            DefaultConnectingIOReactor))
  (:require [clojure.contrib.io :as io]))

(def #^AllowAllHostnameVerifier allow-all-hostname-verifier
     (AllowAllHostnameVerifier.))

(def *default-opts*
     {:worker-threads 1
      :hostname-verifier allow-all-hostname-verifier
      :time-to-live 4000
      :max-total-connections 20
      :http-params (BasicHttpParams.)} )

(def *default-http-opts* (merge *default-opts* {:scheme "http" :port 80}))

(def *default-https-opts* (merge *default-opts* {:scheme "https" :port 443}))

;;   "Defines a callback to execute when an async HTTP request completes.
;;    Param on-complete is a function to run when request completes. That
;;    function should take one param, which is an instance of
;;    org.apache.http.HttpResponse. Param on-cancel is a callback to execute
;;    if the request is cancelled. That function takes no params. Param on-fail
;;    is a function to execute if the request fails. That function takes one
;;    param, which is a java.lang.Exception.

;;    Param latch is a CountDownLatch. Use async-client/countdown-latch to
;;    create this param."

(defrecord HttpCallback
  [on-complete on-cancel on-fail latch]
  org.apache.http.nio.concurrent.FutureCallback
  (completed [this response]
             (try
               (on-complete response)
               (finally
                (. latch countDown))))
  (cancelled [this]
             (try
               (on-cancel)
               (finally
                (. latch countDown))))
  (failed    [this ex]
             (try
               (on-fail ex)
               (finally
                (. latch countDown)))))

(defn #^CountDownLatch countdown-latch
  "Returns a CountdownLatch for managing async IO. Param num-requests must
   specify the number of http requests that the callback will be handling."
  [num-requests]
  (CountDownLatch. num-requests))

(defn #^DefaultConnectingIOReactor io-reactor
  "Returns a new instance of
   org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor"
  [#^Integer worker-count #^org.apache.http.params.HttpParams params]
  (DefaultConnectingIOReactor. worker-count params))

(defn #^SSLLayeringStrategy layering-strategy
  "Returns a new LayeringStrategy for managing SSL connections."
  [#^SSLContext ssl-context #^X509HostnameVerifier hostname-verifier]
  (SSLLayeringStrategy. ssl-context hostname-verifier))

(defn #^Scheme scheme
  "Returns a new org.apache.http.nio.conn.scheme.Scheme. Param name should
   be \"http\" or \"https\". Param port is the port to connect to on the
   remote host."
  [#^String name #^int port #^LayeringStrategy strategy]
  (Scheme. name port strategy))

(defn #^ConnPerRouteBean max-conns-per-route
  "Returns a ConnPerRouteBean describing the maximum number of concurrent
   connections allowed to the specified host. Param conns-per-host-map is
   a hash-map in which keys are strings and values are ints. The key for
   each entry is a fully qualified host name as a string. The value is the
   maximum number of concurrent connections to allow to this host. For example:

   {\"secure.mysite.com\" 12 \"public.theirhost.com\" 8}"
  [conns-per-host]
  (let [conn-bean (ConnPerRouteBean.)]
    (doseq [host (keys conns-per-host)]
      (. conn-bean setMaxForRoute (HttpHost. host) (get conns-per-host host)))
    conn-bean))

(defn set-conn-mgr-params!
  "Sets the MAX_TOTAL_CONNECTIONS and MAX_CONNECTIONS_PER_ROUTE options on
   a BasicHttpParams object"
  [http-params max-conns conns-per-route]
  (. http-params setParameter
     ConnManagerPNames/MAX_TOTAL_CONNECTIONS max-conns)
  (if conns-per-route
    (. http-params setParameter
       ConnManagerPNames/MAX_CONNECTIONS_PER_ROUTE (max-conns-per-route
                                                    conns-per-route)))
  http-params)


(defn #^SchemeRegistry scheme-registry
  "Returns a new instance of a non-blocking Apache SchemeRegistry. Param
   schemes is a seq of schemes to register."
  [schemes]
  (let [registry (SchemeRegistry.)]
    (doseq [scheme schemes]
      (. registry register scheme))
    registry))

(defn #^PoolingClientConnectionManager pooling-conn-manager
  "Returns a PoolingClientConnectionManager"
  [#^org.apache.http.nio.reactor.ConnectingIOReactor ioreactor
   #^org.apache.http.nio.conn.scheme.SchemeRegistry registry
   #^long time-to-live]
  (PoolingClientConnectionManager. ioreactor
                                   registry
                                   time-to-live
                                   TimeUnit/MILLISECONDS))

(defn connection-manager
  "Returns a PoolingClientConnectionManager with the specified options.
   Param options is a hash-map that may include the following:

   :worker-threads         The number of threads the connection manager may use.

   :hostname-verifier      The hostname verifier to use for SSL connections.

   :time-to-live           Connection time-to-live, in milliseconds.

   :max-total-connections  The maximum total number of concurrent connections
                           to all hosts.

   :scheme                 Either \"http\" or \"https\"

   :port                   The port on which to connect. Typically 80 for http
                           and 443 for https.

   To make things easy, you can merge your own hash with *default-http-opts*
   or *default-https-opts*.

   Param conns-per-route is a hash-map specifying the maximum number of
   connections to a specific host. It should be a map like the one below,
   which specifies a maximum of 12 simulataneous connections to secure.mysite.com
   and a maximum of 8 simultaneous connections to public.theirhost.com:

   {\"secure.mysite.com\" 12 \"public.theirhost.com\" 8}

   Param conns-per-route may be nil, in which case, we'll default to 2
   connections per route.

   Typically, you want to create a single connection manager with a reasonably
   large pool of connections, then use that manager for all of the http clients
   you create."
  [options conns-per-route]
  (let [opts (merge *default-http-opts* (or options {}))
        ;; TODO: Fix schemes! We will need an SSL manager!!!
        scheme (scheme (:scheme opts) (:port opts) nil)
        registry (scheme-registry [scheme])
        http-params (set-conn-mgr-params! (:http-params opts)
                                          (:max-total-connections opts)
                                          conns-per-route)
        reactor (io-reactor (:worker-threads opts) http-params)]
    (pooling-conn-manager reactor registry (:time-to-live opts))))


(defn http-client
  "Returns an instance of DefaultHttpAsyncClient that uses the specified
   connection manager. Use the connection-manager function to create one
   instance of a connection manager. Use that one instance for all http
   clients."
  [conn-manager]
  (DefaultHttpAsyncClient. conn-manager))

(defn execute-batch!
  "Executes a batch of HTTP requests, calling the specified callback at the
   end of each request.

   Param client is an HTTP client. Param request-seq is a seq of http request
   objects. These include HttpDelete, HttpGet, HttpHead, HttpOptions, HttpPost
   and HttpPut.

   Param callback is an instance of HttpCallback. See the documentation for
   for async-client/HttpCallback."
  [client request-seq callback latch]
  (try
    (doseq [request request-seq]
      (prn (str "Requesting " request))
      (. client execute request callback))
    ;;(. latch await)
    (finally (. client shutdown))))





;;(comment
  ;; Sample usage

  (defn success [response]
    (io/spit "___output___.html"
             (io/slurp* (.. response getEntity getContent)))
    ;;(println (.. response getRequestLine))
    "OK")
  (defn cancelled [] (println "Request cancelled"))
  (defn failed [ex] (println (str "Request Error: " (. ex getMessage))))

  (def get-requests
       [(HttpGet. "http://www.google.com")
        (HttpGet. "http://www.hotelicopter.com")
        (HttpGet. "http://www.bing.com")
        (HttpGet. "http://www.jsonlint.com")])

  (defn run-gets
    ""
    []
    (let [conn-mgr (connection-manager {} nil)
          client (http-client conn-mgr)
          latch (countdown-latch (count get-requests))
          callback (HttpCallback. success cancelled failed latch)]
      (execute-batch! client get-requests callback latch)))

;;  (run-gets)

;;  )

