## bkrepo基础jdk镜像

FROM bkrepo/linux

LABEL maintainer="blueking"

RUN rpm --rebuilddb && \
    yum -y install initscripts &&\
    yum clean all &&\
    wget https://openresty.org/package/centos/7/x86_64/openresty-openssl-1.1.0h-3.el7.centos.x86_64.rpm -P openrestry &&\
    wget https://openresty.org/package/centos/7/x86_64/openresty-pcre-8.42-1.el7.centos.x86_64.rpm -P openrestry &&\
    wget https://openresty.org/package/centos/7/x86_64/openresty-zlib-1.2.11-3.el7.centos.x86_64.rpm -P openrestry &&\
    wget https://openresty.org/package/centos/7/x86_64/openresty-1.13.6.2-1.el7.centos.x86_64.rpm -P openrestry &&\
    rpm -ivh openrestry/openresty-pcre-8.42-1.el7.centos.x86_64.rpm  &&\
    rpm -ivh openrestry/openresty-zlib-1.2.11-3.el7.centos.x86_64.rpm  &&\
    rpm -ivh openrestry/openresty-openssl-1.1.0h-3.el7.centos.x86_64.rpm  --replacefiles &&\
    rpm -ivh openrestry/openresty-1.13.6.2-1.el7.centos.x86_64.rpm &&\
    rm -rf openrestry
