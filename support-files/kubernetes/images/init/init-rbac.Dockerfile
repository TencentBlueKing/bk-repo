FROM python:3.12.0b1-slim-buster

LABEL maintainer="blueking"

COPY ./ /data/workspace/

WORKDIR /data/workspace