FROM bkrepo/openrestry:0.0.1

LABEL maintainer="Tencent BlueKing Devops"

ENV JAVA_HOME=/usr/local/java/TencentKona-17.0.15.b1
ENV PATH=$PATH:$JAVA_HOME/bin \
    CLASSPATH=.:${JAVA_HOME}/lib

RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo 'Asia/Shanghai' > /etc/timezone && \
    wget -O /etc/yum.repos.d/epel.repo https://mirrors.cloud.tencent.com/repo/epel-7.repo && \
    rpm --rebuilddb && \
    yum -y install initscripts libcurl openssl xz-libs redis && \
    yum clean all && \
    wget https://fastdl.mongodb.org/linux/mongodb-linux-x86_64-rhel70-5.0.9.tgz && \
    tar -zxvf mongodb-linux-x86_64-rhel70-5.0.9.tgz && \
    cp mongodb-linux-x86_64-rhel70-5.0.9/bin/* /usr/local/bin/ && \
    rm -rf mongodb-linux-x86_64-rhel70-5.0.9* && \
    wget https://github.com/Tencent/TencentKona-17/releases/download/TencentKona-17.0.15/TencentKona-17.0.15.b1-jdk_linux-x86_64.tar.gz && \
    mkdir /usr/local/java && \
    tar -xvf TencentKona-17.0.15.b1-jdk_linux-x86_64.tar.gz -C /usr/local/java && \
    rm -rf TencentKona-17.0.15.b1-jdk_linux-x86_64.tar.gz


