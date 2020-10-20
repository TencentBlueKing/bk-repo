FROM bkrepo/jdk

LABEL maintainer="blueking"

ENV MODULE generic

COPY $MODULE.jar /data/bkrepo/
COPY startup.sh /data/bkrepo/
RUN chmod +x /data/bkrepo/startup.sh