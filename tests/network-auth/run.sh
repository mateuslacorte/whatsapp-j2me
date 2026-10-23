#!/bin/bash
set -eu
cd "$(dirname "$0")/../.."
docker run --rm --platform linux/amd64 -v "$PWD:/workspace:ro" -w /workspace j2me-compiler:local bash -c '
set -e
mkdir -p /tmp/auth-tests
javac -encoding UTF-8 -source 1.3 -target 1.3 -cp "$WTK_HOME/lib/cldcapi11.jar:$WTK_HOME/lib/midpapi20.jar" -d /tmp/auth-tests tests/network-auth/com/whatsapp/storage/AccountStore.java tests/network-auth/javax/microedition/io/Connector.java src/com/whatsapp/util/Base64.java src/com/whatsapp/net/NetworkUtil.java tests/network-auth/NetworkAuthTest.java
java -cp "/tmp/auth-tests:$WTK_HOME/lib/cldcapi11.jar:$WTK_HOME/lib/midpapi20.jar" NetworkAuthTest
java -cp "/tmp/auth-tests:$WTK_HOME/lib/cldcapi11.jar:$WTK_HOME/lib/midpapi20.jar" NetworkAuthTest blackberry
'
