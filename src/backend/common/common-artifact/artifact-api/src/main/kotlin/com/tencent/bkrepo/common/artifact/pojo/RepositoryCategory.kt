package com.tencent.bkrepo.common.artifact.pojo

/**
 * 仓库类别枚举类
 */
enum class RepositoryCategory {
    LOCAL, // 本地存储仓库
    REMOTE, // 远程仓库，一般是代理，例如Maven
    VIRTUAL, // 虚拟仓库一般是用来聚合其他仓库
    COMPOSITE // 组合类型仓库
}
