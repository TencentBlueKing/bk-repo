FROM blueking/centos:0.0.1

LABEL maintainer="blueking"

COPY ./ /data/workspace/

RUN echo $'[mongodb-org]\n\
name=MongoDB Repository\n\
baseurl=http://mirrors.cloud.tencent.com/mongodb/yum/el$releasever/\n\
gpgcheck=0\n\
enabled=1'\ > /etc/yum.repos.d/mongodb.repo && \
    rpm --rebuilddb && \
    yum -y install mongodb-org-shell && \
    chmod +x /data/workspace/init-consul.sh && \
    chmod +x /data/workspace/init-mongodb.sh && \
    chmod +x /data/workspace/render_tpl