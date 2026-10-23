#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

TOOLCHAIN_HOME="${J2ME_TOOLCHAIN_HOME:-/opt/java}"
JAVA_HOME="${JAVA_HOME:-$TOOLCHAIN_HOME/jdk1.6.0_45}"
ANT_HOME="${ANT_HOME:-$TOOLCHAIN_HOME/apache-ant-1.8.2}"
MAVEN_HOME="${MAVEN_HOME:-$TOOLCHAIN_HOME/apache-maven-3.0.1}"
WTK_HOME="${WTK_HOME:-$TOOLCHAIN_HOME/WTK2.5.2}"

export JAVA_HOME ANT_HOME MAVEN_HOME WTK_HOME
PATH="$JAVA_HOME/bin:$ANT_HOME/bin:$MAVEN_HOME/bin:$WTK_HOME/bin:$PATH"
export PATH

fail() {
    echo "Error: $*" >&2
    exit 1
}

clean_build() {
    rm -rf "$PROJECT_DIR/tmpclasses" "$PROJECT_DIR/classes"
    rm -f "$PROJECT_DIR/sources.txt" \
        "$PROJECT_DIR/bin/WhatsApp.jar" \
        "$PROJECT_DIR/bin/WhatsApp.jad" \
        "$PROJECT_DIR/bin/MANIFEST.MF"
}

if [ "${1:-}" = "clean" ]; then
    [ "$#" -eq 1 ] || fail "Usage: $0 [ptbr|en|clean]"
    clean_build
    echo "Removed generated build files."
    exit 0
elif [ "$#" -gt 1 ]; then
    fail "Usage: $0 [ptbr|en|clean]"
fi

BUILD_LANGUAGE="${1:-ptbr}"
case "$BUILD_LANGUAGE" in
    ptbr|en) ;;
    *) fail "Usage: $0 [ptbr|en|clean]" ;;
esac
echo "=== Building language: $BUILD_LANGUAGE ==="

require_file() {
    [ -f "$1" ] || fail "Required file not found: $1"
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

check_version() {
    command="$1"
    expected="$2"
    output="$("$command" -version 2>&1)" || fail "Could not run $command."
    echo "$output" | grep -F "$expected" >/dev/null || {
        echo "$output" >&2
        fail "Expected $command version $expected."
    }
}

require_file "$JAVA_HOME/bin/javac"
require_file "$JAVA_HOME/bin/jar"
require_file "$ANT_HOME/bin/ant"
require_file "$MAVEN_HOME/bin/mvn"
require_file "$WTK_HOME/bin/preverify"

require_command grep
require_command find

check_version "$JAVA_HOME/bin/java" '1.6.'
check_version "$ANT_HOME/bin/ant" '1.8.2'
check_version "$MAVEN_HOME/bin/mvn" '3.0.1'

MIDP_CP="$WTK_HOME/lib/cldcapi11.jar:$WTK_HOME/lib/midpapi20.jar:$WTK_HOME/lib/mmapi.jar:$WTK_HOME/lib/wma20.jar:$WTK_HOME/lib/jsr75.jar"
for library in cldcapi11.jar midpapi20.jar mmapi.jar wma20.jar jsr75.jar; do
    require_file "$WTK_HOME/lib/$library"
done

echo "=== Cleaning build directories ==="
rm -rf tmpclasses classes
mkdir -p tmpclasses classes bin
trap 'rm -rf "$PROJECT_DIR/tmpclasses" "$PROJECT_DIR/classes"; rm -f "$PROJECT_DIR/sources.txt"' EXIT

echo "=== Finding Java sources ==="
find src -type f -name '*.java' ! -name '._*' -print | sort > sources.txt
find "locales/$BUILD_LANGUAGE" -type f -name '*.java' -print >> sources.txt
[ -s sources.txt ] || fail "No Java source files found under src."

echo "=== Compiling Java sources ==="
"$JAVA_HOME/bin/javac" \
    -encoding UTF-8 \
    -source 1.3 \
    -target 1.3 \
    -bootclasspath "$MIDP_CP" \
    -d tmpclasses \
    @sources.txt

echo "=== Preverifying classes for CLDC 1.1 ==="
"$WTK_HOME/bin/preverify" \
    -classpath "$MIDP_CP:tmpclasses" \
    -d classes \
    tmpclasses

echo "=== Creating MIDlet manifest ==="
cat > bin/MANIFEST.MF <<'EOF'
MIDlet-1: WhatsApp, /WhatsApp.png, com.whatsapp.WhatsAppMIDlet
MIDlet-Icon: /WhatsApp.png
MIDlet-Name: WhatsApp
MIDlet-Vendor: lacorte.dev
MIDlet-Version: 2.26.50
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0

EOF

echo "=== Packaging JAR ==="
rm -f bin/WhatsApp.jar
"$JAVA_HOME/bin/jar" cfm bin/WhatsApp.jar bin/MANIFEST.MF -C classes .
if [ -d res ]; then
    "$JAVA_HOME/bin/jar" uf bin/WhatsApp.jar -C res .
fi

JAR_SIZE="$(wc -c < bin/WhatsApp.jar | tr -d ' ')"
cat > bin/WhatsApp.jad <<EOF
MIDlet-1: WhatsApp, /WhatsApp.png, com.whatsapp.WhatsAppMIDlet
MIDlet-Icon: /WhatsApp.png
MIDlet-Jar-Size: $JAR_SIZE
MIDlet-Jar-URL: WhatsApp.jar
MIDlet-Name: WhatsApp
MIDlet-Vendor: lacorte.dev
MIDlet-Version: 2.26.50
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
EOF

echo "=============================================="
echo "Built bin/WhatsApp.jar ($JAR_SIZE bytes) successfully!"
echo "=============================================="
