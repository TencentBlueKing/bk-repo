package com.tencent.bkrepo.common.artifact.pojo.configuration.virtual

import com.tencent.bkrepo.common.artifact.pojo.RepositoryIdentify
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration

/**
 * 虚拟仓库配置
 */
open class VirtualConfiguration(
    val repositoryList: List<RepositoryIdentify>
) : RepositoryConfiguration() {
    companion object {
        const val type = "virtual"
    }
}
