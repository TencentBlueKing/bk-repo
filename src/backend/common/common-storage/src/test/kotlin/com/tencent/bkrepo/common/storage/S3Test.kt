package com.tencent.bkrepo.common.storage

import org.junit.jupiter.api.Test
import com.qcloud.s1.cos.COSClient
import com.qcloud.s1.cos.ClientConfig
import com.qcloud.s1.cos.auth.BasicCOSCredentials
import com.qcloud.s1.cos.exception.CosServiceException
import com.qcloud.s1.cos.region.Region
import com.qcloud.s1.cos.utils.IOUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import org.apache.http.HttpStatus
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.core.io.ClassPathResource


/**
 * TODO:
 *
 * @author: carrypan
 * @date: 2019-09-16
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3Test {

    private val appid = "75070"
    private val secretId = "hm1KDDUVEvUqWRbXxRlbD8tX"
    private val secretKey = "YJ3FAeZ4nEM8jEHDu2Quh1RBsn/PPfALZD"
    private val region = "gzc"
    private val bucket = "test"
    private val host = "vod.tencent-cloud.com"

    private lateinit var cosClient: COSClient

    private val filename = "3c8cbd91a4bcc9de596b782347e3978268875ad7cc0522921cc01551d5aed19f"


    @BeforeAll
    fun beforeAll() {
        val credentials = BasicCOSCredentials(appid, secretId, secretKey)
        val clientConfig = ClientConfig(Region(region))
        clientConfig.host = host
        cosClient = COSClient(credentials, clientConfig)
    }


    @Test
    fun cosTest() {
        cosClient!!.listObjects(bucket).objectSummaries.forEach { println(it.key) }
    }

    @Test
    fun existTest() {
        var exists = true
        try {
            cosClient!!.getobjectMetadata(bucket, filename)

        } catch (cosServiceException: CosServiceException) {
            exists = cosServiceException.statusCode != HttpStatus.SC_NOT_FOUND
        }
        println(exists)
    }

}