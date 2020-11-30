#!/bin/bash

function info() {
    echo -e "\033[32m $1 \033[0m"
}

cd $(dirname $0)
working_dir=$(pwd)
root_dir=${working_dir%/*/*/*}

set -e
source ../build.env
mkdir -p tmp

## 编译frontend
info "编译frontend..."
yarn --cwd $root_dir/src/frontend install
yarn --cwd $root_dir/src/frontend run public
info "编译frontend完成"

## 打包gateway镜像
info "构建gateway镜像..."
rm -rf tmp/*
cp -rf $root_dir/src/frontend/frontend tmp/
cp -rf $root_dir/src/gateway tmp/gateway
cp -rf gateway/startup.sh tmp/
cp -rf $root_dir/support-files/scripts/render_tpl tmp/
cp -rf $root_dir/support-files/templates tmp/
docker build -f gateway/gateway.Dockerfile -t $hub/bkrepo/gateway:$bkrepo_version tmp --network=host
docker push $hub/bkrepo/gateway:$bkrepo_version
docker tag $hub/bkrepo/gateway:$bkrepo_version bkrepo/gateway
info "构建gateway镜像完成"

## 编译backend
info "编译backend..."
gradle -p $root_dir/src/backend build \
-x test \
-x :composer:build \
-x :composer:boot-composer:build \
-x :composer:biz-composer:build \
-x :monitor:boot-monitor:build \
-x :pypi:build \
-x :pypi:boot-pypi:build \
-x :pypi:biz-pypi:build \
-x :nuget:build \
-x :nuget:boot-nuget:build \
-x :nuget:biz-nuget:build \
-x :replication:build \
-x :replication:boot-replication:build \
-x :replication:biz-replication:build
info "编译backend完成"

## 构建backend镜像
info "构建backend镜像..."
backends=(repository auth generic docker helm dockerapi)
for service in ${backends[@]};
do
    info "build $service start..."
    rm -rf tmp/*
    cp backend/startup.sh tmp/
    cp $root_dir/src/backend/$service/boot-$service/build/libs/*.jar tmp/$service.jar
    docker build -f backend/$service.Dockerfile -t $hub/bkrepo/$service:$bkrepo_version tmp --network=host
    docker push $hub/bkrepo/$service:$bkrepo_version
    docker tag $hub/bkrepo/$service:$bkrepo_version bkrepo/$service
    info "build $service finish..."
done

## 构建init镜像
info "构建init镜像..."
rm -rf tmp/*
cp -rf init/init-mongodb.sh tmp/
cp -rf init/init-consul.sh tmp/
cp -rf $root_dir/support-files/scripts/render_tpl tmp/
cp -rf $root_dir/support-files/templates tmp/
cp -rf $root_dir/support-files/sql/init-data.js tmp/
docker build -f init/init.Dockerfile -t $hub/bkrepo/init:$bkrepo_version tmp --no-cache --network=host
docker push $hub/bkrepo/init:$bkrepo_version
docker tag $hub/bkrepo/init:$bkrepo_version bkrepo/init
info "构建init镜像完成"

rm -rf tmp
