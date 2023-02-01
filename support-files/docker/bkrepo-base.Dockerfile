FROM blueking/centos:0.0.1

LABEL maintainer="Tencent BlueKing Devops"

ENV JAVA_HOME=/usr/local/java/TencentKona-8.0.2-252
ENV PATH=$PATH:$JAVA_HOME/bin \
    CLASSPATH=.:${JAVA_HOME}/lib

RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo 'Asia/Shanghai' > /etc/timezone && \
    wget -O /etc/yum.repos.d/epel.repo https://mirrors.cloud.tencent.com/repo/epel-7.repo && \
    rpm --rebuilddb && \
    yum -y install initscripts libcurl openssl xz-libs redis && \
    yum clean all && \
    wget https://mirrors.cloud.tencent.com/openresty/rhel/7/x86_64/openresty-openssl-1.1.0h-3.el7.x86_64.rpm -P openresty && \
    wget https://mirrors.cloud.tencent.com/openresty/rhel/7/x86_64/openresty-pcre-8.42-1.el7.x86_64.rpm -P openresty && \
    wget https://mirrors.cloud.tencent.com/openresty/rhel/7/x86_64/openresty-zlib-1.2.11-3.el7.x86_64.rpm -P openresty && \
    wget https://mirrors.cloud.tencent.com/openresty/rhel/7/x86_64/openresty-1.13.6.2-1.el7.x86_64.rpm -P openresty && \
    rpm -ivh openresty/openresty-pcre-8.42-1.el7.x86_64.rpm  && \
    rpm -ivh openresty/openresty-zlib-1.2.11-3.el7.x86_64.rpm  && \
    rpm -ivh openresty/openresty-openssl-1.1.0h-3.el7.x86_64.rpm  --replacefiles && \
    rpm -ivh openresty/openresty-1.13.6.2-1.el7.x86_64.rpm && \
    rm -rf openresty && \
    wget https://fastdl.mongodb.org/linux/mongodb-linux-x86_64-rhel70-5.0.9.tgz && \
    tar -zxvf mongodb-linux-x86_64-rhel70-5.0.9.tgz && \
    cp mongodb-linux-x86_64-rhel70-5.0.9/bin/* /usr/local/bin/ && \
    rm -rf mongodb-linux-x86_64-rhel70-5.0.9* && \
    wget https://github.com/Tencent/TencentKona-8/releases/download/v8.0.2-GA/TencentKona8.0.2.b1_jdk_linux-x86_64_8u252.tar.gz && \
    mkdir /usr/local/java && \
    tar -xvf TencentKona8.0.2.b1_jdk_linux-x86_64_8u252.tar.gz -C /usr/local/java && \
    rm -rf TencentKona8.0.2.b1_jdk_linux-x86_64_8u252.tar.gz
