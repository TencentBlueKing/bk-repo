package com.tencent.bkrepo.common.storage.innercos

import com.tencent.bkrepo.common.storage.core.StorageCredentials

/**
 * inner cos 身份认证信息
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
data class InnerCosCredentials(
        override val credentialsKey: String,
        val appId: String,
        val secretId: String,
        val secretKey: String,
        val host: String,
        val regionName: String,
        val bucketName: String
) : StorageCredentials