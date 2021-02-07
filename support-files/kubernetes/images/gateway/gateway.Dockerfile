FROM blueking/openresty:0.0.1

LABEL maintainer="Tencent BlueKing Devops"

COPY ./ /data/workspace/


RUN ls -l /usr/local/openresty/nginx

RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo 'Asia/Shanghai' > /etc/timezone

RUN ls -l /usr/local/openresty/nginx

RUN cp -r  /data/workspace/gateway /usr/local/openresty/nginx/conf

RUN ls -l /usr/local/openresty/nginx

RUN mkdir -p /usr/local/openresty/nginx/run/

RUN ls -l /usr/local/openresty/nginx

RUN chmod +x /data/workspace/startup.sh &&\
    chmod +x /data/workspace/render_tpl

RUN ls -l /usr/local/openresty/nginx
