package com.tencent.bkrepo.common.storage.innercos

import com.tencent.bkrepo.common.storage.innercos.client.CosClient

class InnerCosClient(
    val bucketName: String,
    val cosClient: CosClient
)
