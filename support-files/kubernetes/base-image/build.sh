#!/bin/bash

source ../build.env

# 打包Linux镜像
docker build -f dockerfile/bkrepo-linux.Dockerfile -t ${hub}/bkrepo/linux:${linux_version} . --network=host
docker push ${hub}/bkrepo/linux:${linux_version}
docker tag ${hub}/bkrepo/linux:${linux_version} bkrepo/linux

## 打包jdk镜像
docker build -f dockerfile/bkrepo-jdk.Dockerfile -t ${hub}/bkrepo/jdk:${jdk_version} . --network=host
docker push ${hub}/bkrepo/jdk:${jdk_version}
docker tag ${hub}/bkrepo/jdk:${jdk_version} bkrepo/jdk

## 打包openresty镜像
docker build -f dockerfile/bkrepo-openresty.Dockerfile -t ${hub}/bkrepo/openresty:${openresty_version} . --network=host
docker push ${hub}/bkrepo/openresty:${openresty_version}
docker tag ${hub}/bkrepo/openresty:${openresty_version} bkrepo/openresty