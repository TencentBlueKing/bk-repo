package com.tencent.bkrepo.common.storage.innercos

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import com.tencent.cos.COSClient
import com.tencent.cos.ClientConfig
import com.tencent.cos.auth.BasicCOSCredentials
import com.tencent.cos.exception.CosServiceException
import com.tencent.cos.model.DeleteObjectRequest
import com.tencent.cos.model.GetObjectRequest
import com.tencent.cos.model.ObjectMetadata
import com.tencent.cos.model.PutObjectRequest
import com.tencent.cos.region.Region
import com.tencent.cos.transfer.TransferManager
import java.io.File
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.apache.http.HttpStatus

/**
 * tencent inner cos 文件存储实现类
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class InnerCosFileStorage(
    locateStrategy: LocateStrategy,
    properties: InnerCosProperties
) : AbstractFileStorage<InnerCosCredentials, InnerCosClient>(locateStrategy, properties) {

    private val executor = ThreadPoolExecutor(100, 200, 5L, TimeUnit.SECONDS,
            LinkedBlockingQueue(1024), ThreadFactoryBuilder().setNameFormat("innercos-storage-uploader-pool-%d").build(),
            ThreadPoolExecutor.AbortPolicy())

    override fun createClient(credentials: InnerCosCredentials): InnerCosClient {
        val basicCOSCredentials = BasicCOSCredentials(credentials.secretId, credentials.secretKey)
        val clientConfig = ClientConfig(Region(credentials.region))
        return InnerCosClient(credentials.bucket, COSClient(basicCOSCredentials, clientConfig))
    }

    override fun onClientRemoval(credentials: InnerCosCredentials, client: InnerCosClient) {
        client.cosClient.shutdown()
        super.onClientRemoval(credentials, client)
    }

    override fun store(path: String, filename: String, file: File, client: InnerCosClient) {
        // 支持根据文件的大小自动选择单文件上传或者分块上传
        val transferManager = TransferManager(client.cosClient, executor, false)
        val putObjectRequest = PutObjectRequest(client.bucketName, filename, file)
        val upload = transferManager.upload(putObjectRequest)
        // 等待传输结束
        upload.waitForCompletion()
        transferManager.shutdownNow()
    }

    override fun store(path: String, filename: String, inputStream: InputStream, client: InnerCosClient) {
        val fileSize = inputStream.available().toLong()
        // 支持根据文件的大小自动选择单文件上传或者分块上传
        val transferManager = TransferManager(client.cosClient, executor, false)
        val objectMetadata = ObjectMetadata().apply { contentLength = fileSize }
        val putObjectRequest = PutObjectRequest(client.bucketName, filename, inputStream, objectMetadata)
        transferManager.upload(putObjectRequest)
    }

    override fun delete(path: String, filename: String, client: InnerCosClient) {
        val deleteObjectRequest = DeleteObjectRequest(client.bucketName, filename)
        client.cosClient.deleteObject(deleteObjectRequest)
    }

    override fun load(path: String, filename: String, client: InnerCosClient): File? {
        return if (exist(path, filename, client)) {
            val file = createFile(filename)
            val getObjectRequest = GetObjectRequest(client.bucketName, filename)
            val transferManager = TransferManager(client.cosClient, executor, false)
            val download = transferManager.download(getObjectRequest, file)
            download.waitForCompletion()
            return file
        } else null
    }

    override fun exist(path: String, filename: String, client: InnerCosClient): Boolean {
        var exists = true
        try {
            client.cosClient.getObjectMetadata(client.bucketName, filename)
        } catch (cosServiceException: CosServiceException) {
            exists = cosServiceException.statusCode != HttpStatus.SC_NOT_FOUND
        }
        return exists
    }

    private fun createFile(filename: String): File? {
        return localFileCache!!.touch(filename)
    }
}
