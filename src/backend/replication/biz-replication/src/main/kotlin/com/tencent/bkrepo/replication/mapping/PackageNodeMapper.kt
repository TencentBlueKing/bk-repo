package com.tencent.bkrepo.replication.mapping

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType

interface PackageNodeMapper {
    /**
     * 匹配仓库类型
     */
    fun type(): RepositoryType

    /**
     * 匹配对应node节点的fullPath
     */
    fun map(key: String, version: String, extension: Map<String, Any>): List<String>
}
