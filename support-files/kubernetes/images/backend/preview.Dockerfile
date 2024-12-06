FROM blueking/jdk:0.0.2

LABEL maintainer="Tencent BlueKing Devops"

# 内置一些常用的中文字体，避免普遍性乱码
# COPY fonts/* /usr/share/fonts/chinese/
RUN yum -y install wqy-zenhei wqy-microhei fontconfig && \
    fc-cache -fv && \
    yum -y install ca-certificates && \
    # 安装 glibc-common 以支持中文本地化
    yum -y install glibc-common && \
    # 安装必要的字体包
    yum -y install wqy-zenhei wqy-microhei ttf-dejavu fontconfig wget && \
    # 设置中文环境
    localedef -i zh_CN -c -f UTF-8 -A /usr/share/locale/locale.alias zh_CN.UTF-8 && \
    export LANG=zh_CN.UTF-8 && \
    # 设置时区
    export DEBIAN_FRONTEND=noninteractive && \
    yum -y install tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    # 安装 LibreOffice 所需的库
    yum -y install libXrender libXinerama libXt libXext libfreetype cairo cups libX11 nss && \
    # 清理缓存
    yum clean all && \
    # 下载 LibreOffice RPM 包
    wget https://downloadarchive.documentfoundation.org/libreoffice/old/7.6.7.1/rpm/x86_64/LibreOffice_7.6.7.1_Linux_x86-64_rpm.tar.gz -O libreoffice_rpm.tar.gz && \
    tar -zxf libreoffice_rpm.tar.gz -C /tmp && \
    # 安装 RPM 包
    cd /tmp/LibreOffice_7.6.7.1_Linux_x86-64_rpm/RPMS && \
    yum -y install *.rpm && \
    # 清理临时文件
    rm -rf /tmp/* && rm -rf /var/lib/apt/lists/*

ENV BK_REPO_HOME=/data/workspace \
    BK_REPO_LOGS_DIR=/data/workspace/logs \
    BK_REPO_SERVICE_PREFIX=bkrepo- \
    BK_REPO_PROFILE=dev

RUN mkdir -p /data/tools && \
    curl -o /data/tools/arthas.jar https://arthas.aliyun.com/arthas-boot.jar

COPY ./ /data/workspace/
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo 'Asia/Shanghai' > /etc/timezone && \
    chmod +x /data/workspace/startup.sh
WORKDIR /data/workspace
CMD /data/workspace/startup.sh
