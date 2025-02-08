FROM python:3.12.0b1-slim-buster

LABEL maintainer="blueking"

COPY ./ /data/workspace/

RUN pip3 install requests

WORKDIR /data/workspace