## bkrepo基础linux镜像

FROM centos:centos7.2.1511

LABEL maintainer="blueking"

## 使用自己的yum源加速
COPY linux/CentOS-Base.repo /etc/yum.repos.d/
COPY linux/mongodb.repo /etc/yum.repos.d/
## 安装consul
COPY linux/consul /usr/bin/

RUN yum clean all &&\
    yum makecache -y &&\
    yum -y install kde-l10n-Chinese glibc-common wget &&\
    yum clean all  &&\
    localedef -c -f UTF-8 -i zh_CN zh_CN.utf8  && \
    ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo 'Asia/Shanghai' > /etc/timezone && \
    chmod +x /usr/bin/consul

ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8