package com.tencent.bkrepo.common.storage.innercos

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.storage.cache.CachedFileStorage
import com.tencent.bkrepo.common.storage.cache.FileCache
import com.tencent.bkrepo.common.storage.pojo.InnerCosCredentials
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
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
 * tencent inner cos 文件存储实现类
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class InnerCosFileStorage(
    fileCache: FileCache,
    locateStrategy: LocateStrategy,
    properties: InnerCosProperties
) : CachedFileStorage<InnerCosCredentials, InnerCosClient>(fileCache, locateStrategy, properties) {

    private val executor = ThreadPoolExecutor(100, 200, 5L, TimeUnit.SECONDS,
            LinkedBlockingQueue(1024), ThreadFactoryBuilder().setNameFormat("inner-cos-storage-uploader-pool-%d").build(),
            ThreadPoolExecutor.AbortPolicy())

    override fun createClient(credentials: InnerCosCredentials): InnerCosClient {
        val basicCOSCredentials = BasicCOSCredentials(credentials.secretId, credentials.secretKey)
        val clientConfig = ClientConfig(Region(credentials.region))
        if (credentials.modId != null && credentials.cmdId != null) {
            // 开启cl5
            val cl5Info = CL5Info(credentials.modId!!, credentials.cmdId!!, credentials.timeout)
            clientConfig.endpointResolver = CL5EndpointResolver(cl5Info)
        }
        return InnerCosClient(credentials.bucket, COSClient(basicCOSCredentials, clientConfig))
    }

    override fun onClientRemoval(credentials: InnerCosCredentials, client: InnerCosClient) {
        client.cosClient.shutdown()
        super.onClientRemoval(credentials, client)
    }

    override fun doStore(path: String, filename: String, cachedFile: File, client: InnerCosClient) {
        val transferManager = TransferManager(client.cosClient, executor, false)
        val putObjectRequest = PutObjectRequest(client.bucketName, filename, cachedFile)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        transferManager.shutdownNow()
    }

    override fun doDelete(path: String, filename: String, client: InnerCosClient) {
        val deleteObjectRequest = DeleteObjectRequest(client.bucketName, filename)
        client.cosClient.deleteObject(deleteObjectRequest)
    }

    override fun doLoad(path: String, filename: String, file: File, client: InnerCosClient): File? {
        val getObjectRequest = GetObjectRequest(client.bucketName, filename)
        val transferManager = TransferManager(client.cosClient, executor, false)
        val download = transferManager.download(getObjectRequest, file)
        download.waitForCompletion()
        transferManager.shutdownNow()
        return file
    }

    override fun checkExist(path: String, filename: String, client: InnerCosClient): Boolean {
        var exists = true
        try {
            client.cosClient.getObjectMetadata(client.bucketName, filename)
        } catch (cosServiceException: CosServiceException) {
            exists = cosServiceException.statusCode != HttpStatus.SC_NOT_FOUND
        } catch(ignored: Exception) {
            exists = false
        }
        return exists
    }
}
