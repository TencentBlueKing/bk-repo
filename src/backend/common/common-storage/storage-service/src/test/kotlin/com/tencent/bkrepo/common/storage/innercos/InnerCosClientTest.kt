package com.tencent.bkrepo.common.storage.innercos

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.cos.COSClient
import com.tencent.cos.ClientConfig
import com.tencent.cos.auth.BasicCOSCredentials
import com.tencent.cos.internal.Constants
import com.tencent.cos.model.PutObjectRequest
import com.tencent.cos.region.Region
import com.tencent.cos.transfer.TransferManager
import com.tencent.cos.transfer.TransferManagerConfiguration
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class InnerCosClientTest {

    private val executor = ThreadPoolExecutor(100, 2000, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(10000), ThreadFactoryBuilder().setNameFormat("inner-cos-uploader-pool-%d").build(),
        ThreadPoolExecutor.AbortPolicy())
    private val secretId = "hm1KDDUVEvUqWRbXxRlbD8tX"
    private val secretKey = "YJ3FAeZ4nEM8jEHDu2Quh1RBsn/PPfALZD"
    private val regionName = "gzc"
    private val bucket = "local-75070"
    private val client = createClient()
    private val transferManager = createTransferManager()

    private fun createClient(): COSClient {
        val basicCOSCredentials = BasicCOSCredentials(secretId, secretKey)
        val clientConfig = ClientConfig().apply {
            region = Region(regionName)
            signExpired = 10 // second
            socketTimeout = 60 * 1000 // millsSecond
            maxConnectionsCount = 10
        }
        return COSClient(basicCOSCredentials, clientConfig)
    }

    private fun createTransferManager(): TransferManager {
        val transferManager = TransferManager(client, executor, true)
        transferManager.configuration = TransferManagerConfiguration().apply {
            multipartUploadThreshold = 1L * Constants.MB
            minimumUploadPartSize = 1L * Constants.MB
        }
        return transferManager
    }

    @Test
    fun testSingleFileUpload() {
        val file = File("/Users/carrypan/Desktop/1b.txt")
        val key = "/temp/1b.txt"
        val putObjectRequest = PutObjectRequest(bucket, key, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
    }

    @Test
    fun testMultipart1MFileUpload() {
        val file = File("/Users/carrypan/Desktop/1M.txt")
        val key = "/temp/1M.txt"
        val putObjectRequest = PutObjectRequest(bucket, key, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        transferManager.shutdownNow(true)
    }

    @Test
    fun testMultipart2MFileUpload() {
        val file = File("/Users/carrypan/Desktop/2M.txt")
        val key = "/temp/2M.txt"
        val putObjectRequest = PutObjectRequest(bucket, key, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        transferManager.shutdownNow(true)
    }

    @Test
    fun testMultipart5MFileUpload() {
        val file = File("/Users/carrypan/Desktop/5M.txt")
        val key = "/temp/5M.txt"
        val putObjectRequest = PutObjectRequest(bucket, key, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        transferManager.shutdownNow(true)
    }

    @Test
    fun testMultipart10MFileUpload() {
        val file = File("/Users/carrypan/Desktop/10M.txt")
        val key = "/temp/10M.txt"
        val putObjectRequest = PutObjectRequest(bucket, key, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        transferManager.shutdownNow(true)
    }


    @Test
    fun testMultipart100MFileUpload() {
        val file = File("/Users/carrypan/Desktop/100M.txt")
        val key = "/temp/100M.txt"
        val putObjectRequest = PutObjectRequest(bucket, key, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        transferManager.shutdownNow(true)
    }

}