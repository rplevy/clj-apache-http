lein test!
JAR_NAME=$(echo $(lein jar) | sed 's/.*\/\(clj.*\.jar\)/\1/g')
lein pom
echo 'scp pom.xml '$JAR_NAME' clojars@clojars.org:'
