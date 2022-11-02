# 源码编译

## 下载源码

下载v1.3.8-rc1版本
```shell
wget https://github.com/TencentBlueKing/bk-repo/archive/refs/tags/v1.3.8-rc1.zip
unzip v1.3.8-rc1.zip
cd bk-repo-1.3.8-rc1/src/frontend/
yarn install && yarn public
cd ../backend/
./gradlew build
```

从git 下载
```shell
git clone https://github.com/TencentBlueKing/bk-repo.git
cd bk-repo/src/frontend/
yarn install && yarn public
cd ../backend/
./gradlew build
```

构建产物
```
|-- src
    |-- backend
    |   |-- release #后端产物
    |-- frontend
    |   |-- frontend
    |   |   |-- ui  #前端产物
        
```

TIPS: 后端项目gradle编译比较久，可以添加-x test参数跳过测试，加快编译速度。
