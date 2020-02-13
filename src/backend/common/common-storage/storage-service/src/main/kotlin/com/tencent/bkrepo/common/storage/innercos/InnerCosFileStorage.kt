package com.tencent.bkrepo.common.storage.innercos

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.cos.COSClient
import com.tencent.cos.ClientConfig
import com.tencent.cos.auth.BasicCOSCredentials
import com.tencent.cos.cl5.CL5Info
import com.tencent.cos.endpoint.CL5EndpointResolver
import com.tencent.cos.internal.Constants.MB
import com.tencent.cos.model.DeleteObjectRequest
import com.tencent.cos.model.GetObjectRequest
import com.tencent.cos.model.PutObjectRequest
import com.tencent.cos.region.Region
import com.tencent.cos.transfer.TransferManager
import com.tencent.cos.transfer.TransferManagerConfiguration
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 内部cos文件存储实现类
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
open class InnerCosFileStorage : AbstractFileStorage<InnerCosCredentials, InnerCosClient>() {

    private val executor = ThreadPoolExecutor(128, 1024, 10L, TimeUnit.SECONDS,
        LinkedBlockingQueue(8192), ThreadFactoryBuilder().setNameFormat("inner-cos-storage-uploader-pool-%d").build(),
        ThreadPoolExecutor.AbortPolicy())

    override fun store(path: String, filename: String, file: File, client: InnerCosClient) {
        val transferManager = createTransferManager(client.cosClient)
        val putObjectRequest = PutObjectRequest(client.bucketName, filename, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        transferManager.shutdownNow()
    }

    override fun load(path: String, filename: String, received: File, client: InnerCosClient): File? {
        val transferManager = createTransferManager(client.cosClient)
        val getObjectRequest = GetObjectRequest(client.bucketName, filename)
        val download = transferManager.download(getObjectRequest, received)
        download.waitForCompletion()
        transferManager.shutdownNow()
        return received
    }

    override fun delete(path: String, filename: String, client: InnerCosClient) {
        val deleteObjectRequest = DeleteObjectRequest(client.bucketName, filename)
        client.cosClient.deleteObject(deleteObjectRequest)
    }

    override fun exist(path: String, filename: String, client: InnerCosClient): Boolean {
        return try {
            return client.cosClient.getObjectMetadata(client.bucketName, filename) != null
        } catch (ignored: Exception) {
            false
        }
    }

    override fun onCreateClient(credentials: InnerCosCredentials): InnerCosClient {
        require(credentials.secretId.isNotBlank())
        require(credentials.secretKey.isNotBlank())
        require(credentials.region.isNotBlank())
        require(credentials.bucket.isNotBlank())
        val basicCOSCredentials = BasicCOSCredentials(credentials.secretId, credentials.secretKey)
        val clientConfig = ClientConfig(Region(credentials.region))
        if (credentials.modId != null && credentials.cmdId != null) {
            // 开启cl5
            val cl5Info = CL5Info(credentials.modId!!, credentials.cmdId!!, credentials.timeout)
            clientConfig.endpointResolver = CL5EndpointResolver(cl5Info)
        }
        return InnerCosClient(credentials.bucket, COSClient(basicCOSCredentials, clientConfig))
    }

    override fun getDefaultCredentials() = storageProperties.innercos

    private fun createTransferManager(cosClient: COSClient): TransferManager {
        val transferManager = TransferManager(cosClient, executor, false)
        transferManager.configuration = TransferManagerConfiguration().apply {
            multipartUploadThreshold = 20L * MB
            minimumUploadPartSize = 10L * MB
        }
        return transferManager
    }
}
