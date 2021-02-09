FROM blueking/openresty:0.0.1

LABEL maintainer="Tencent BlueKing Devops"

COPY ./ /data/workspace/

RUN cp -r /usr/local/openresty /usr/local/openresty_bak && \
    rm -rf /usr/local/openresty_bak/nginx/conf && \
    ln -s /data/workspace/gateway /usr/local/openresty_bak/nginx/conf && \
    mkdir -p /usr/local/openresty_bak/nginx/run/ && \
    rm -rf /usr/local/openresty && \
    cp -r /usr/local/openresty_bak /usr/local/openresty && \
    ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo 'Asia/Shanghai' > /etc/timezone && \
    chmod +x /data/workspace/startup.sh &&\
    chmod +x /data/workspace/render_tpl
