# backend
蓝鲸制品库后端项目

## 模块说明

- common 公共模块，进行统一配置与定制化，提供微服务引用
  - common-api  接口相关，包含公共pojo类、工具类、公共常量、接口异常等
  - common-auth 认证相关，为微服务提供统一的用户认证
  - common-client 微服务客户端，提供feign client的统一配置
  - common-mongo MongoDB封装
  - common-redis Redis封装，提供常用操作以及分布式锁
  - common-service 微服务相关，提供微服务、web、swagger、日志相关的统一配置
  
- boot-assembly 统一打包模块，将所有模块打包为一个单体jar快速运行

- xxx 微服务模块
  - api-xxx 微服务提供对外的api，包含接口信息、pojo类、常量信息等。
  - biz-xxx 微服务业务实现模块
  - boot-xxx 微服务启动模块

## swagger说明

`common-service`模块中已经集成了swagger相关配置，依赖该模块则默认开启`swagger`功能。

默认已经将`/error`和`/actuator/**`相关接口排除在外。
 
### swagger开关

通过配置`swagger.enabled=false`可以关闭`swagger`功能，生产环境中可以关闭。

### swagger地址

${base-path}/v2/api-docs

配合chrome浏览器插件`Swagger UI Console`使用

## 启动说明

### idea
直接运行`xxx-boot`模块下`XXXApplication.kt`的`main`函数即可

### gradle
因为使用了`SpringBoot`的`gradle`插件，所以通过`bootRun`任务即可运行

```
运行xxx微服务
./gradlew xxx:boot-xxx:bootRun

```

## 打包说明

因为使用了`SpringBoot`的`gradle`插件，所以通过`bootJar`任务即可完成打包

```
打包单个微服务
./gradlew xxx:boot-xxx:bootJar

打包所有微服务
./gradlew bootJar

```

`SpringBoot`插件默认会关闭`jar`任务，所以在`build.gradle`中有如下配置:

```
def isBootProject = project.name.startsWith("boot-")

jar.enabled = !isBootProject
bootJar.enabled = isBootProject
bootRun.enabled = isBootProject
```
以`boot-`开头的模块才会开启`bootJar`和`bootRun`，可以打包为可执行jar，
其它模块作为依赖包，开启`jar`任务打包为jar依赖包

## 代码规范

通过`gradle`的`ktlint`任务进行扫描

```
代码格式检查
./gradlew ktlint

代码格式化
./gradlew ktlintFormat

```