package com.tencent.bkrepo.common.artifact.pojo

/**
 * 仓库类型
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
enum class RepositoryType {
    NONE,
    GENERIC,
    DOCKER,
    MAVEN,
    PYPI,
    NPM,
    OPDATA,
    HELM,
    COMPOSER,
    RPM
}
