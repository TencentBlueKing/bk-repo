#! /bin/sh
##初始化配置
/data/workspace/render_tpl -u -m . -p /data/workspace /data/workspace/templates/gateway*
/data/workspace/render_tpl -u -m . -p /data/workspace /data/workspace/frontend/ui/frontend#ui#index.html

##启动程序
/usr/local/openresty/nginx/sbin/nginx
tail -f /dev/null