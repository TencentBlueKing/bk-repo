#! /bin/sh
nohup consul agent -datacenter=dc -domain=bkrepo -data-dir=/tmp -join=consul-server > /dev/null 2>&1 &

mkdir -p /data/logs
java -server \
     -Dsun.jnu.encoding=UTF-8 \
     -Dfile.encoding=UTF-8 \
     -Xloggc:/data/logs/gc.log \
     -XX:+PrintTenuringDistribution \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=oom.hprof \
     -XX:ErrorFile=error_sys.log \
     -Xms$BK_REPO_JVM_XMS \
     -Xmx$BK_REPO_JVM_XMX \
     -jar $MODULE.jar \
     --spring.profiles.active=$BK_REPO_ENV

tail -f /dev/null