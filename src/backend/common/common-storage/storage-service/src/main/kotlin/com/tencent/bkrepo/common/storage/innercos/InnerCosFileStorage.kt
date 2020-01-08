package com.tencent.bkrepo.common.storage.innercos

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.cos.COSClient
import com.tencent.cos.ClientConfig
import com.tencent.cos.auth.BasicCOSCredentials
import com.tencent.cos.cl5.CL5Info
import com.tencent.cos.endpoint.CL5EndpointResolver
import com.tencent.cos.exception.CosServiceException
import com.tencent.cos.model.DeleteObjectRequest
import com.tencent.cos.model.GetObjectRequest
import com.tencent.cos.model.PutObjectRequest
import com.tencent.cos.region.Region
import com.tencent.cos.transfer.TransferManager
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.apache.http.HttpStatus

/**
 * 内部cos文件存储实现类
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
open class InnerCosFileStorage : AbstractFileStorage<InnerCosCredentials, InnerCosClient>() {

    private val executor = ThreadPoolExecutor(100, 200, 5L, TimeUnit.SECONDS,
        LinkedBlockingQueue(1024), ThreadFactoryBuilder().setNameFormat("inner-cos-storage-uploader-pool-%d").build(),
        ThreadPoolExecutor.AbortPolicy())

    override fun store(path: String, filename: String, file: File, storageCredentials: StorageCredentials) {
        val client = getClient(storageCredentials)
        val transferManager = TransferManager(client.cosClient, executor, false)
        val putObjectRequest = PutObjectRequest(client.bucketName, filename, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        transferManager.shutdownNow()
    }

    override fun load(path: String, filename: String, received: File, storageCredentials: StorageCredentials): File? {
        val client = getClient(storageCredentials)
        val getObjectRequest = GetObjectRequest(client.bucketName, filename)
        val transferManager = TransferManager(client.cosClient, executor, false)
        val download = transferManager.download(getObjectRequest, received)
        download.waitForCompletion()
        transferManager.shutdownNow()
        return received
    }

    override fun delete(path: String, filename: String, storageCredentials: StorageCredentials) {
        val client = getClient(storageCredentials)
        val deleteObjectRequest = DeleteObjectRequest(client.bucketName, filename)
        client.cosClient.deleteObject(deleteObjectRequest)
    }

    override fun exist(path: String, filename: String, storageCredentials: StorageCredentials): Boolean {
        val client = getClient(storageCredentials)
        var exists = true
        try {
            client.cosClient.getObjectMetadata(client.bucketName, filename)
        } catch (cosServiceException: CosServiceException) {
            exists = cosServiceException.statusCode != HttpStatus.SC_NOT_FOUND
        } catch (ignored: Exception) {
            exists = false
        }
        return exists
    }

    override fun onCreateClient(credentials: InnerCosCredentials): InnerCosClient {
        val basicCOSCredentials = BasicCOSCredentials(credentials.secretId, credentials.secretKey)
        val clientConfig = ClientConfig(Region(credentials.region))
        if (credentials.modId != null && credentials.cmdId != null) {
            // 开启cl5
            val cl5Info = CL5Info(credentials.modId!!, credentials.cmdId!!, credentials.timeout)
            clientConfig.endpointResolver = CL5EndpointResolver(cl5Info)
        }
        requireNotNull(credentials.bucket) { "Bucket cannot be null." }
        return InnerCosClient(
            credentials.bucket!!,
            COSClient(basicCOSCredentials, clientConfig)
        )
    }

    override fun getDefaultCredentials() = storageProperties.innercos
}
