#!/bin/sh
# Gradle wrapper script for Unix
# Generated for OpenLight

##############################################################################
# Determine the Java command to use to start the JVM.
##############################################################################
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java > /dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found."
fi

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

GRADLE_OPTS="${GRADLE_OPTS:-} -Xmx2048m"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Get absolute path of this script
APP_HOME="`pwd -P`"

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
