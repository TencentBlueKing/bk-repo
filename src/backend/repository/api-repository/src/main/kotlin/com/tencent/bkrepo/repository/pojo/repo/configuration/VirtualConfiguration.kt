package com.tencent.bkrepo.repository.pojo.repo.configuration

import com.tencent.bkrepo.repository.pojo.repo.RepositoryIdentify

/**
 * 虚拟仓库配置
 * @author: carrypan
 * @date: 2019/11/26
 */
open class VirtualConfiguration(
    val repositoryList: List<RepositoryIdentify>
): RepositoryConfiguration() {
    companion object {
        const val type = "virtual"
    }
}