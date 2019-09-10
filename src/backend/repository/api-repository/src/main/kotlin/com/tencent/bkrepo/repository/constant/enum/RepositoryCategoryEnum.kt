package com.tencent.bkrepo.repository.constant.enum

/**
 * 仓库类别枚举类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
enum class RepositoryCategoryEnum {
    LOCAL, // 本地存储仓库
    REMOTE, // 远程仓库，一般是代理，例如Maven
    VITURAL // 虚拟仓库一般是用来聚合其他仓库
}
