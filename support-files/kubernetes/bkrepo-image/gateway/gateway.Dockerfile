FROM bkrepo/openresty

LABEL maintainer="blueking"

COPY gateway /data/bkrepo/gateway
COPY frontend /data/bkrepo/frontend
COPY templates /data/bkrepo/templates
COPY startup.sh /data/bkrepo/
COPY render_tpl /data/bkrepo/

## lua日志目录
RUN mkdir -p /data/logs/nginx/ &&\
    chown -R nobody:nobody /data/logs/nginx/ &&\
    rm -rf /usr/local/openresty/nginx/conf &&\
    ln -s  /data/bkrepo/gateway /usr/local/openresty/nginx/conf &&\
    mkdir -p /usr/local/openresty/nginx/run/ &&\
    chmod +x /data/bkrepo/startup.sh &&\
    chmod +x /data/bkrepo/render_tpl