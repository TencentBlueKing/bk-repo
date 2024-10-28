package com.tencent.bkrepo.common.artifact.pojo

import com.tencent.bkrepo.common.api.constant.CharPool

/**
 * 仓库标识类
 */
data class RepositoryId(val projectId: String, val repoName: String) {
    override fun toString(): String {
        return StringBuilder(projectId).append(CharPool.SLASH).append(repoName).toString()
    }
}
