package com.tencent.bkrepo.repository.pojo.repo.configuration

/**
 * 本地仓库配置
 * @author: carrypan
 * @date: 2019/11/26
 */
open class LocalConfiguration: RepositoryConfiguration() {
    companion object {
        const val type = "local"
    }
}