FROM python:3

LABEL maintainer="blueking"

COPY ./ /data/workspace/

WORKDIR /data/workspace