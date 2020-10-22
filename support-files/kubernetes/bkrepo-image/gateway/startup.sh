#! /bin/sh

##初始化配置
/data/bkrepo/render_tpl -u -m bkrepo -p /data /data/bkrepo/templates/gateway*
/data/bkrepo/render_tpl -u -m bkrepo -p /data /data/bkrepo/frontend/repository/frontend#repository#index.html
/data/bkrepo/render_tpl -u -m bkrepo -p /data /data/bkrepo/frontend/ui/frontend#ui#index.html

##启动程序
#nohup consul agent -datacenter=dc -domain=bkrepo -data-dir=/tmp -join=consul-server &
/usr/local/openresty/nginx/sbin/nginx
tail -f /dev/null