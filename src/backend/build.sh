#!/usr/bin/env bash

core_service=("helm" "oci" "rpm" "npm" "maven" "pypi" "conan" "nuget" "generic" "cargo" "s3" "huggingface" "lfs"
             "git" "svn" "composer" "ddc")

rm -rf release
mkdir -p release

./gradlew clean

services="$1"
build_args="$2"
echo $services
echo $build_args
for service in ${services//,/ }
do
    echo $service
    if [ ${service} = "job-schedule" ] || [ ${service} = "job-worker" ];then
       ./gradlew job:boot-${service}:build ${build_args} -x test
    elif [[ " ${core_service[@]} " == *" ${service} "* ]];then
       ./gradlew :core:${service}:boot-${service}:build ${build_args} -x test
    else
       ./gradlew ${service}:boot-${service}:build ${build_args} -x test
    fi
     mv ./release/boot-${service}.jar ./release/service-${service}.jar
done

