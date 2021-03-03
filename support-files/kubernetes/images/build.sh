#!/bin/bash

function info() {
    echo -e "\033[32m $1 \033[0m"
}

cd $(dirname $0)
working_dir=$(pwd)
root_dir=${working_dir%/*/*/*}
backend_dir=$root_dir/src/backend
frontent_dir=$root_dir/src/frontend
gateway_dir=$root_dir/src/gateway

set -e
source ../build.env
# 创建临时目录
mkdir -p $working_dir/tmp
tmp_dir=$working_dir/tmp
# 执行退出时自动清理tmp目录
trap 'rm -rf $tmp_dir' EXIT TERM

# 编译frontend
info "编译frontend..."
yarn --cwd $frontent_dir install
yarn --cwd $frontent_dir run public
info "编译frontend完成"

# 打包gateway镜像
info "构建gateway镜像..."
rm -rf tmp/*
cp -rf $frontent_dir/frontend tmp/
cp -rf $gateway_dir tmp/gateway
cp -rf gateway/startup.sh tmp/
cp -rf $root_dir/scripts/render_tpl tmp/
cp -rf $root_dir/support-files/templates tmp/
docker build -f gateway/gateway.Dockerfile -t $hub/bkrepo/gateway:$bkrepo_version tmp --network=host
docker push $hub/bkrepo/gateway:$bkrepo_version
docker tag $hub/bkrepo/gateway:$bkrepo_version bkrepo/gateway
info "构建gateway镜像完成"

# 构建backend镜像
backends=(repository auth generic docker helm dockerapi)
for service in ${backends[@]};
do
    info "构建$service镜像..."
    $backend_dir/gradlew -p $backend_dir :$service:boot-$service:build -x test
    rm -rf tmp/*
    cp backend/startup.sh tmp/
    cp $backend_dir/release/boot-$service-*.jar tmp/$service.jar
    docker build -f backend/$service.Dockerfile -t $hub/bkrepo/$service:$bkrepo_version tmp --network=host
    docker push $hub/bkrepo/$service:$bkrepo_version
    docker tag $hub/bkrepo/$service:$bkrepo_version bkrepo/$service
    info "构建$service镜像完成"
done

# 构建init镜像
info "构建init镜像..."
rm -rf tmp/*
cp -rf init/init-mongodb.sh tmp/
cp -rf init/init-consul.sh tmp/
cp -rf $root_dir/scripts/render_tpl tmp/
cp -rf $root_dir/support-files/templates tmp/
cp -rf $root_dir/support-files/sql/init-data.js tmp/
docker build -f init/init.Dockerfile -t $hub/bkrepo/init:$bkrepo_version tmp --no-cache --network=host
docker push $hub/bkrepo/init:$bkrepo_version
docker tag $hub/bkrepo/init:$bkrepo_version bkrepo/init
info "构建init镜像完成"
