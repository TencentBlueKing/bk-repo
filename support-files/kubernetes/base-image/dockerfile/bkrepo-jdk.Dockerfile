## bkrepo基础jdk镜像

FROM bkrepo/linux

LABEL maintainer="blueking"

COPY jdk /usr/local/jdk
RUN chmod +x /usr/local/jdk/bin/*
ENV JAVA_HOME /usr/local/jdk
ENV PATH $PATH:$JAVA_HOME/bin
