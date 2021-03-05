#! /bin/sh
LOGS_DIR=/data/logs
if [ -n "$BK_REPO_LOGS_DIR" ]; then
    LOGS_DIR=$BK_REPO_LOGS_DIR
fi
mkdir -p $LOGS_DIR

java -server \
     -Dsun.jnu.encoding=UTF-8 \
     -Dfile.encoding=UTF-8 \
     -Xloggc:$LOGS_DIR/gc.log \
     -XX:+PrintTenuringDistribution \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=oom.hprof \
     -XX:ErrorFile=$LOGS_DIR/error_sys.log \
     -jar /data/workspace/app.jar
