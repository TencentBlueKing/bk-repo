FROM bkrepo/linux

LABEL maintainer="blueking"

COPY templates /data/bkrepo/templates
COPY init-consul.sh /data/bkrepo/
COPY init-mongodb.sh /data/bkrepo/
COPY init-data.js /data/bkrepo/
COPY render_tpl /data/bkrepo/

RUN rpm --rebuilddb && \
    yum -y install mongodb-org-shell && \
    chmod +x /data/bkrepo/init-consul.sh && \
    chmod +x /data/bkrepo/init-mongodb.sh && \
    chmod +x /data/bkrepo/render_tpl