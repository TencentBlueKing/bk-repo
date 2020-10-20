#!/bin/sh

set -e

echo "导入环境变量开始..."
source ../build.env
mkdir -p tmp && rm -rf tmp/*
echo "导入环境变量完成"

working_dir=$(pwd)
frontend_dir=../../../src/frontend
gateway_dir=../../../src/gateway
backend_dir=../../../src/backend
## 初始化数据库
echo "初始化数据库开始..."
echo "初始化数据库完成"

## 编译frontend
echo "编译frontend..."
#yarn --cwd $frontend_dir run public
echo "编译frontend完成"

##打包gateway镜像
echo "打包gateway镜像开始..."
rm -rf tmp/*
cp -rf ${frontend_dir}/frontend tmp/
cp -rf ${gateway_dir} tmp/gateway
cp -rf gateway/startup.sh tmp/
cp -rf ../../scripts/render_tpl tmp/
cp -rf ../../templates tmp/
docker build -f gateway/gateway.Dockerfile -t $hub/bkrepo/gateway:$bkrepo_version tmp --network=host
docker push $hub/bkrepo/gateway:$bkrepo_version
docker tag $hub/bkrepo/gateway:$bkrepo_version bkrepo/gateway
echo "打包gateway镜像完成"

## 编译backend
echo "编译backend..."
cd $backend_dir
# ./gradlew build -x test
cd $working_dir
echo "编译frontend完成"

## 打包backend镜像
echo "打包backend镜像开始..."
backends=(repository auth generic)
for var in ${backends[@]};
do
    echo "build $var start..."
    rm -rf tmp/*
    cp backend/startup.sh tmp/
    cp $backend_dir/$var/boot-$var/build/libs/*.jar tmp/$var.jar
    docker build -f backend/$var.Dockerfile -t $hub/bkrepo/$var:$bkrepo_version tmp --network=host
    docker push $hub/bkrepo/$var:$bkrepo_version
    docker tag $hub/bkrepo/$var:$bkrepo_version bkrepo/$var
    echo "build $var finish..."
done

## 打包初始化镜像
echo "打包初始化镜像中..."
rm -rf tmp/*
cp -rf init/init-mongo.sh tmp/
cp -rf init/init-consul.sh tmp/
cp -rf ../../scripts/render_tpl tmp/
cp -rf ../../templates tmp/
cp -rf ../../sql/init-data.js tmp/
docker build -f init/init.Dockerfile -t $hub/bkrepo/init:$bkrepo_version tmp --no-cache --network=host
docker push $hub/bkrepo/init:$bkrepo_version
docker tag $hub/bkrepo/init:$bkrepo_version bkrepo/init
echo "打包初始化镜像完成"

rm -rf tmp
