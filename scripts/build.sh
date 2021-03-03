#!/bin/bash

function info() {
    echo -e "\033[32m $1 \033[0m"
}

cd $(dirname $0)
working_dir=$(pwd)
root_dir=${working_dir%/*}
backend_dir=$root_dir/src/backend
frontent_dir=$root_dir/src/frontend
gateway_dir=$root_dir/src/gateway
templates_dir=$root_dir/support-files/templates

# 创建临时目录
mkdir -p $root_dir/tmp
tmp_dir=$root_dir/tmp
# 执行退出时自动清理tmp目录
trap 'rm -rf $tmp_dir' EXIT TERM

info "编译frontend..."
yarn --cwd $frontent_dir install
yarn --cwd $frontent_dir run public

info "编译backend..."
$backend_dir/gradlew -p $backend_dir -x test

info "拷贝frontend..."
cp -rf $frontent_dir/frontend $tmp_dir

info "拷贝backend..."
mkdir -p $tmp_dir/backend
for file in $backend_dir/release/boot-*.jar; do
  service_name=$(basename $file | awk -F'[-.]' '{print $2}')
  cp $file $tmp_dir/backend/boot-$service_name.jar
done

info "拷贝templates..."
cp -rf $templates_dir $tmp_dir

info "拷贝scripts..."
cp -rf $working_dir $tmp_dir

info "拷贝gateway..."
cp -rf $gateway_dir $tmp_dir

info "打包bkrepo.tar.gz..."
# 创建bin目录
mkdir -p $root_dir/bin
cd $tmp_dir
tar -zcf $root_dir/bin/bkrepo.tar.gz *

info "Success! 文件保存到$root_dir/bin/bkrepo.tar.gz"
