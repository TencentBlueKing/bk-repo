package com.tencent.bkrepo.replication.mapping

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType

interface PackageNodeMapping {
    /**
     * 匹配仓库类型
     */
    fun match(type: RepositoryType): Boolean

    /**
     * 匹配对应node节点的fullPath
     */
    fun handle(key: String, version: String, ext: Map<String, Any>): List<String>
}
