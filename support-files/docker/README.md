## 蓝盾仓库镜像
- bkrepo-base.Dockerfile：仓库基础镜像，包含仓库相关依赖，如openresty、mongodb、jdk等
- bkrepo.Dockerfile：仓库镜像，可以一键拉起仓库应用，根据github上发布的bkrepo-slim.tar.gz部署。