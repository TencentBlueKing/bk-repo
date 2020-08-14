package com.tencent.bkrepo.common.artifact.pojo

/**
 * 仓库类型
 */
enum class RepositoryType {
    NONE,
    GENERIC,
    DOCKER,
    MAVEN,
    PYPI,
    NPM,
    HELM,
    COMPOSER,
    RPM
}
