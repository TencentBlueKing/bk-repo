package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 代理源模型
 */
@Document("proxy_channel")
data class TProxyChannel (
    /**
     * id
     */
    var id: String? = null,
    /**
     * 是否为公有源
     */
    var public: Boolean,
    /**
     * 代理源名称
     */
    var name: String,
    /**
     * 代理源url
     */
    var url: String,
    /**
     * 代理源仓库类型
     */
    var repoType: RepositoryType,
    /**
     * 代理源认证凭证key
     */
    var credentialKey: String? = null,
    /**
     * 代理源认证用户名
     */
    var username: String? = null,
    /**
     * 代理源认证密码
     */
    var password: String? = null
)