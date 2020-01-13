package com.tencent.bkrepo.common.storage.s3

import com.amazonaws.services.s3.AmazonS3

/**
 *
 * @author: carrypan
 * @date: 2020/1/13
 */
class S3Client(
    val bucketName: String,
    val s3Client: AmazonS3
)
