package com.tencent.bkrepo.scanner.pojo

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType

enum class ScanSchemeType(val repositoryType: RepositoryType) {
    GENERIC(RepositoryType.GENERIC),
    DOCKER(RepositoryType.DOCKER),
    MAVEN(RepositoryType.MAVEN),
    PYPI(RepositoryType.PYPI),
    NPM(RepositoryType.NPM),
    HELM(RepositoryType.HELM),
    RDS(RepositoryType.RDS),
    COMPOSER(RepositoryType.COMPOSER),
    RPM(RepositoryType.RPM),
    NUGET(RepositoryType.NUGET),
    GIT(RepositoryType.GIT),
    OCI(RepositoryType.OCI),

    // 许可扫描
    GENERIC_LICENSE(RepositoryType.GENERIC),
    MAVEN_LICENSE(RepositoryType.MAVEN),
    NONE(RepositoryType.NONE);

    companion object {
        fun ofValueOrDefault(type: String?): ScanSchemeType {
            type ?: return NONE
            val upperCase = type.toUpperCase()
            return values().find { it.name == upperCase } ?: NONE
        }

        fun ofRepositoryType(type: String?): RepositoryType {
            return ofValueOrDefault(type).repositoryType
        }
    }
}
