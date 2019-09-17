package com.tencent.bkrepo.common.storage.local

import com.tencent.bkrepo.common.storage.core.StorageCredentials

/**
 * 本地文件存储身份认证信息
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
data class LocalStorageCredentials(
        override val credentialsKey: String,
        val directory: String
) : StorageCredentials