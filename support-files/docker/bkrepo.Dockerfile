FROM bkrepo/bkrepo-base:0.0.3

LABEL maintainer="Tencent BlueKing Devops"

ENV BK_REPO_HOME=/data/workspace/bkrepo \
    BK_REPO_LOGS_DIR=/bkrepo-data/logs \
    BK_REPO_DATA_PATH=/bkrepo-data/store \
    BK_REPO_REDIS_PATH=/bkrepo-data/redis \
    BK_REPO_MONGO_PATH=/bkrepo-data/mongo \
    BK_REPO_HOST="bkrepo.example.com" \
    BK_REPO_HTTP_PORT="80" \
    BK_REPO_AUTHORIZATION="Platform MThiNjFjOWMtOTAxYi00ZWEzLTg5YzMtMWY3NGJlOTQ0YjY2OlVzOFpHRFhQcWs4NmN3TXVrWUFCUXFDWkxBa00zSw==" \
    BK_REPO_SERVICE_NAME="bk-repo" \
    BK_REPO_DOMAIN="127.0.0.1:8080" \
    BK_REPO_DOCKER_HOST="docker.bkrepo.example.com" \
    BK_REPO_STATIC_HOST="static.bkrepo.example.com" \
    BK_REPO_EXT_IP="[::]" \
    BK_REPO_DOCKER_HTTP_PORT="80" \
    BK_REPO_PROFILE="dev"

COPY ./bkrepo-slim.tar.gz /data/workspace/bkrepo-slim.tar.gz

RUN yum install -y net-tools && yum clean all && \
    tar -xvf /data/workspace/bkrepo-slim.tar.gz -C /data/workspace && \
    rm -rf /data/workspace/bkrepo-slim.tar.gz && \
    chmod +x $BK_REPO_HOME/scripts/render_tpl && \
    chmod +x $BK_REPO_HOME/scripts/bk-repo-all-in-one-startup.sh

WORKDIR $BK_REPO_HOME
CMD $BK_REPO_HOME/scripts/bk-repo-all-in-one-startup.sh