#! /bin/sh

##启动redis
echo "启动redis..."
REDIS_LOG_PATH=$BK_REPO_REDIS_PATH/log
REDIS_DATA_PATH=$BK_REPO_REDIS_PATH/data
mkdir -p $REDIS_LOG_PATH
mkdir -p $REDIS_DATA_PATH
redis-server --daemonize yes --logfile $REDIS_LOG_PATH/redis.log --dir $REDIS_DATA_PATH --appendonly yes

##启动mongodb
echo "启动mongodb..."
MONGO_DATA_PATH=$BK_REPO_MONGO_PATH/lib/mongo
MONGO_LOG_PATH=$BK_REPO_MONGO_PATH/log/mongodb
mkdir -p $MONGO_DATA_PATH
mkdir -p $MONGO_LOG_PATH
mongod --dbpath $MONGO_DATA_PATH --logpath $MONGO_LOG_PATH/mongod.log --fork
##初始化mongodb
echo "初始化mongodb..."
mongo mongodb://127.0.0.1:27017/bkrepo $BK_REPO_HOME/support-files/sql/init-data.js

mkdir -p $BK_REPO_LOGS_DIR/nginx
chmod 777 $BK_REPO_LOGS_DIR/nginx

mkdir -p $BK_REPO_LOGS_DIR/bkrepo
chmod 777 $BK_REPO_LOGS_DIR/bkrepo

##初始化网关配置
echo "渲染网关配置..."
touch repo.env
$BK_REPO_HOME/scripts/render_tpl -u -p $BK_REPO_HOME -m . -e repo.env $BK_REPO_HOME/support-files/templates/gateway#vhosts#bkrepo.server.conf
$BK_REPO_HOME/scripts/render_tpl -u -p $BK_REPO_HOME -m . -e repo.env $BK_REPO_HOME/support-files/templates/gateway#vhosts#bkrepo.docker.server.conf
$BK_REPO_HOME/scripts/render_tpl -u -p $BK_REPO_HOME -m . -e repo.env $BK_REPO_HOME/support-files/templates/gateway#server.common.conf
$BK_REPO_HOME/scripts/render_tpl -u -p $BK_REPO_HOME -m . -e repo.env $BK_REPO_HOME/support-files/templates/gateway#lua#init.lua
$BK_REPO_HOME/scripts/render_tpl -u -p $BK_REPO_HOME -m . -e repo.env -E BK_REPO_SHOW_ANALYST_MENU=true $BK_REPO_HOME/support-files/templates/frontend#ui#index.html

##启动网关程序
echo "启动网关..."
rm -rf /usr/local/openresty/nginx/conf
ln -s $BK_REPO_HOME/gateway /usr/local/openresty/nginx/conf
mkdir -p /usr/local/openresty/nginx/run/
cd /usr/local/openresty/nginx
/usr/local/openresty/nginx/sbin/nginx

##启动assembly程序
echo "启动boot-assembly..."
cd $BK_REPO_HOME/backend/assembly/
source $BK_REPO_HOME/backend/assembly/service.env
java -server \
     -Dsun.jnu.encoding=UTF-8 \
     -Dfile.encoding=UTF-8 \
     -Xlog:gc*:file=$BK_REPO_LOGS_DIR/bkrepo/gc.log:time,level,tags \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=oom.hprof \
     -XX:ErrorFile=$BK_REPO_LOGS_DIR/bkrepo/error_sys.log \
     -Dspring.profiles.active=$BK_REPO_PROFILE \
     -Doci.domain=$BK_REPO_DOCKER_HOST \
     -Doci.authUrl=http://$BK_REPO_DOCKER_HOST/v2/auth \
     -Dhelm.domain=http://$BK_REPO_HOST/helm \
     -Dlogging.path=$BK_REPO_LOGS_DIR/bkrepo \
     -Dstorage.filesystem.path=$BK_REPO_DATA_PATH \
     $BK_REPO_JVM_OPTION \
     $MAIN_CLASS