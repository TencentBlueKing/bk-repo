FROM blueking/jdk:0.0.2

LABEL maintainer="Tencent BlueKing Devops"

RUN yum -y install ca-certificates && \
    yum -y install glibc-common wget bzip2 && \
    # 安装必要的字体包
    wget https://sourceforge.net/projects/wqy/files/wqy-microhei/0.2.0-beta/wqy-microhei-0.2.0-beta.tar.gz -O /tmp/wqy-microhei.tar.gz && \
    mkdir -p /usr/share/fonts/wenquanyi && \
    tar -zxvf /tmp/wqy-microhei.tar.gz -C /usr/share/fonts/wenquanyi --strip-components=1 && \
    rm -f /tmp/wqy-microhei.tar.gz && \
    wget http://wenq.org/daily/zenhei/wqy-zenhei-0.9.47-nightlybuild.tar.gz -O /tmp/wqy-zenhei.tar.gz && \
    mkdir -p /usr/share/fonts/wenquanyi && \
    tar -zxvf /tmp/wqy-zenhei.tar.gz -C /usr/share/fonts/wenquanyi --strip-components=1 && \
    rm -f /tmp/wqy-zenhei.tar.gz && \
    wget https://github.com/dejavu-fonts/dejavu-fonts/releases/download/version_2_37/dejavu-fonts-ttf-2.37.tar.bz2 -O /tmp/dejavu-fonts.tar.bz2 && \
    mkdir -p /usr/share/fonts/dejavu && \
    tar -jxvf /tmp/dejavu-fonts.tar.bz2 -C /usr/share/fonts/dejavu --strip-components=1 && \
    rm -f /tmp/dejavu-fonts.tar.bz2 && \
    yum -y install fontconfig && \
    fc-cache -fv && \
    # 设置中文环境
    localedef -i zh_CN -c -f UTF-8 -A /usr/share/locale/locale.alias zh_CN.UTF-8 && \
    # 设置时区
    export DEBIAN_FRONTEND=noninteractive && \
    yum -y install tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    # 安装 LibreOffice 所需的库
    yum -y install libXrender libXinerama libXt libXext libfreetype cairo cups libX11 nss && \
    # 清理缓存
    yum clean all && \
    # 安装LibreOffice
    wget https://downloadarchive.documentfoundation.org/libreoffice/old/7.6.7.1/rpm/x86_64/LibreOffice_7.6.7.1_Linux_x86-64_rpm.tar.gz -O libreoffice_rpm.tar.gz && \
    tar -zxf libreoffice_rpm.tar.gz -C /tmp && \
    cd /tmp/LibreOffice_7.6.7.1_Linux_x86-64_rpm/RPMS && \
    yum -y install *.rpm && \
    rm -rf /tmp/* && rm -rf /var/lib/apt/lists/*

# 设置环境变量，支持中文
ENV LANG=zh_CN.utf-8
ENV LC_ALL=zh_CN.utf-8
ENV LC_CTYPE=zh_CN.utf-8
RUN echo "LANG=zh_CN.utf-8" >> /etc/environment

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
