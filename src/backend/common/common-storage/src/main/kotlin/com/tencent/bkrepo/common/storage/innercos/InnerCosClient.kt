package com.tencent.bkrepo.common.storage.innercos

import com.tencent.cos.COSClient

/**
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class InnerCosClient(
    val bucketName: String,
    val cosClient: COSClient
)