package com.tencent.bkrepo.repository.pojo.repo

/**
 * 仓库存储身份信息
 *
 * @author: carrypan
 * @date: 2019-10-16
 */
data class StorageCredentials(
    var type: String,
    var credentials: String
)
