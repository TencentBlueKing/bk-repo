package com.tencent.bkrepo.common.storage.event

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials

data class StoreFailureEvent(
    val path: String,
    val filename: String,
    val fileLocation: String,
    val storageCredentials: StorageCredentials,
    val exception: Exception
)
