package com.tencent.bkrepo.common.storage.event

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials

/**
 *
 * @author: carrypan
 * @date: 2020/1/8
 */
data class StoreFailureEvent(
    val path: String,
    val filename: String,
    val fileLocation: String,
    val storageCredentials: StorageCredentials,
    val exception: Exception
)
