package com.tencent.bkrepo.common.artifact.pojo.configuration.virtual

import com.tencent.bkrepo.common.artifact.pojo.RepositoryIdentify
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration

/**
 * 虚拟仓库配置
 * @author: carrypan
 * @date: 2019/11/26
 */
open class VirtualConfiguration(
    val repositoryList: List<RepositoryIdentify>
) : RepositoryConfiguration() {
    companion object {
        const val type = "virtual"
    }
}
