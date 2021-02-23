# bk-repo 前端部署文档

蓝鲸repo前端（frontend目录下）

## 系统要求

nodejs版本 8.0.0及以上

## 安装说明

- 1、打包并部署相应的vue工程
进入到src/frontend目录下
```
# 先全局安装yarn
npm install -g yarn
# 然后执行install
yarn install
# 然后安装每个子服务的依赖
yarn start
# 最后执行打包命令
yarn public
```

执行完这命令后，会在src/frontend目录下生成一个frontend的文件夹，里面是BK-REPO前端打包后生成的资源文件


最后将生成的frontend文件夹copy到`__INSTALL_PATH__/__MODULE__/`目录下
    
    最终前端部署目录结构为：
```
__INSTALL_PATH__/__MODULE__/frontend/ui
```
