#!/bin/sh
# gradlew minimal: unduh gradle-wrapper.jar jika belum ada, dan pilih JDK 8-17 (utamakan 11, lalu 17, terakhir 8).
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -s "$JAR" ]; then
  URL="https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
  mkdir -p "$APP_HOME/gradle/wrapper"
  echo "gradle-wrapper.jar belum ada, mengunduh..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$JAR"
  fi
  if [ ! -s "$JAR" ]; then
    rm -f "$JAR"
    echo "Gagal mengunduh gradle-wrapper.jar. Cek internet, atau salin jar dari project lain." >&2
    exit 1
  fi
fi

# Versi mayor sebuah java (8, 11, 17, ...). Kosong jika gagal.
java_major() {
  v=$("$1" -version 2>&1 | head -n 1 | sed -n 's/.*version "\([0-9]*\)\.\([0-9]*\).*/\1 \2/p')
  set -- $v
  [ -z "$1" ] && return
  if [ "$1" = "1" ]; then echo "$2"; else echo "$1"; fi
}

# Kandidat: JAVA_HOME, java di PATH, lalu lokasi umum (AndroidIDE / Termux / Linux)
CANDIDATES="$JAVA_HOME/bin/java java"
for base in "$PREFIX/opt" /opt/java /opt /data/data/com.itsaky.androidide/files/usr/opt /data/data/com.itsaky.androidide/files/usr/lib/jvm /usr/lib/jvm /usr/opt; do
  for d in "$base"/*; do
    [ -x "$d/bin/java" ] && CANDIDATES="$CANDIDATES $d/bin/java"
  done
  [ -x "$base/openjdk/bin/java" ] && CANDIDATES="$CANDIDATES $base/openjdk/bin/java"
done

# Catat "mayor|path" tiap JDK yang valid (8-17). Java 8u212 ARM64 sering crash (bug JIT),
# jadi JDK 11/17 diutamakan kalau ada. Bisa dipaksa lewat FURUARC_JAVA=/path/ke/bin/java.
FOUND=""
for c in ${FURUARC_JAVA:-} $CANDIDATES; do
  case "$c" in /bin/java) continue;; esac
  if command -v "$c" >/dev/null 2>&1 || [ -x "$c" ]; then
    m=$(java_major "$c")
    if [ -n "$m" ] && [ "$m" -ge 8 ] && [ "$m" -le 17 ]; then FOUND="$FOUND $m|$c"; fi
  fi
done

JAVACMD=""
JAVAMAJOR=""
if [ -n "$FURUARC_JAVA" ] && [ -x "$FURUARC_JAVA" ]; then
  JAVACMD="$FURUARC_JAVA"; JAVAMAJOR=$(java_major "$FURUARC_JAVA")
else
  for pref in 11 17 16 15 14 13 12 10 9 8; do
    for f in $FOUND; do
      if [ "${f%%|*}" = "$pref" ]; then JAVACMD="${f#*|}"; JAVAMAJOR="$pref"; break 2; fi
    done
  done
fi

if [ -z "$JAVACMD" ]; then
  echo "Java 8-17 tidak ditemukan. Java yang terdeteksi:" >&2
  java -version >&2
  echo "Set JAVA_HOME ke JDK 8 sampai 17." >&2
  exit 1
fi
# Gradle daemon & semua JVM anak memakai JDK yang sama dengan yang dipilih di sini
export JAVA_HOME=$(dirname "$(dirname "$JAVACMD")")
echo "Memakai Java: $JAVACMD"

# Java 8 (8u212) di ARM64 crash (SIGSEGV di libjvm.so) saat JIT C2 mengompilasi LambdaForm.
# JAVA_TOOL_OPTIONS dibaca oleh SEMUA JVM yang dijalankan dari sini (Gradle daemon, Kotlin daemon,
# dan compiler Kotlin fallback di proses terpisah), jadi C2 dimatikan di semuanya.
if [ "$JAVAMAJOR" = "8" ]; then
  OPT="-XX:TieredStopAtLevel=1"
  # Cadangan terakhir: FURUARC_XINT=1 ./gradlew ...  (tanpa JIT sama sekali, sangat lambat tapi tidak crash karena JIT)
  [ "$FURUARC_XINT" = "1" ] && OPT="$OPT -Xint"
  case "$JAVA_TOOL_OPTIONS" in
    *TieredStopAtLevel*) ;;
    *) export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $OPT" ;;
  esac
fi

# AGP 4.2.2 memakai refleksi ke internal JDK; di JDK 9+ itu diblokir kecuali dibuka lewat --add-opens.
if [ -n "$JAVAMAJOR" ] && [ "$JAVAMAJOR" -ge 9 ]; then
  OPENS=""
  for p in java.io java.lang java.lang.invoke java.lang.reflect java.net java.nio java.nio.charset java.nio.file \
           java.security java.text java.util java.util.concurrent java.util.concurrent.atomic java.util.regex \
           java.util.zip java.util.jar sun.nio.ch sun.nio.fs sun.security.action sun.net.www.protocol.jar; do
    OPENS="$OPENS --add-opens=java.base/$p=ALL-UNNAMED"
  done
  for p in com.sun.tools.javac.api com.sun.tools.javac.code com.sun.tools.javac.comp com.sun.tools.javac.file \
           com.sun.tools.javac.main com.sun.tools.javac.model com.sun.tools.javac.parser com.sun.tools.javac.processing \
           com.sun.tools.javac.tree com.sun.tools.javac.util; do
    OPENS="$OPENS --add-opens=jdk.compiler/$p=ALL-UNNAMED"
  done
  # JAVA_TOOL_OPTIONS dibaca semua JVM anak (Gradle daemon, Kotlin daemon, worker)
  export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $OPENS"
fi

exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
