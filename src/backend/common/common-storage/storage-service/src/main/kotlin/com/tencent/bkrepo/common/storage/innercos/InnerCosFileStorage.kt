package com.tencent.bkrepo.common.storage.innercos

import com.tencent.bkrepo.common.artifact.stream.Range
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
import com.tencent.cos.model.ObjectMetadata
import com.tencent.cos.model.PutObjectRequest
import com.tencent.cos.region.Region
import com.tencent.cos.transfer.TransferManager
import com.tencent.cos.transfer.TransferManagerConfiguration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executor
import javax.annotation.Resource

/**
 * 内部cos文件存储实现类
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
open class InnerCosFileStorage : AbstractFileStorage<InnerCosCredentials, InnerCosClient>() {

    @Resource
    private lateinit var taskAsyncExecutor: Executor

    private var defaultTransferManager: TransferManager? = null

    override fun store(path: String, filename: String, file: File, client: InnerCosClient) {
        val transferManager = getTransferManager(client)
        val putObjectRequest = PutObjectRequest(client.bucketName, filename, file)
        val upload = transferManager.upload(putObjectRequest)
        upload.waitForCompletion()
        shutdownTransferManager(transferManager)
    }

    override fun store(path: String, filename: String, inputStream: InputStream, size: Long, client: InnerCosClient) {
        val metadata = ObjectMetadata().apply { contentLength = size }
        client.cosClient.putObject(client.bucketName, filename, inputStream, metadata)
    }

    override fun load(path: String, filename: String, received: File, client: InnerCosClient): File? {
        val transferManager = getTransferManager(client)
        val getObjectRequest = GetObjectRequest(client.bucketName, filename)
        val download = transferManager.download(getObjectRequest, received)
        download.waitForCompletion()
        shutdownTransferManager(transferManager)
        return received
    }

    override fun load(path: String, filename: String, range: Range, client: InnerCosClient): InputStream? {
        val getObjectRequest = GetObjectRequest(client.bucketName, filename)
        if (range.isPartialContent()) {
            getObjectRequest.setRange(range.start, range.end)
        }
        return client.cosClient.getObject(getObjectRequest).objectContent
    }

    override fun delete(path: String, filename: String, client: InnerCosClient) {
        if (exist(path, filename, client)) {
            val deleteObjectRequest = DeleteObjectRequest(client.bucketName, filename)
            client.cosClient.deleteObject(deleteObjectRequest)
        }
    }

    override fun exist(path: String, filename: String, client: InnerCosClient): Boolean {
        return try {
            return client.cosClient.getObjectMetadata(client.bucketName, filename).instanceLength >= 0
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
        val clientConfig = ClientConfig().apply {
            region = Region(credentials.region)
            signExpired = 3600 * 24 * 7 // second
            socketTimeout = 60 * 1000 // millsSecond
            maxConnectionsCount = 2048
        }
        if (credentials.modId != null && credentials.cmdId != null) {
            // 开启cl5
            val cl5Info = CL5Info(credentials.modId!!, credentials.cmdId!!, credentials.timeout)
            clientConfig.endpointResolver = CL5EndpointResolver(cl5Info)
        }
        val cosClient = COSClient(basicCOSCredentials, clientConfig)
        return InnerCosClient(credentials.bucket, cosClient)
    }

    private fun getTransferManager(innerCosClient: InnerCosClient): TransferManager {
        return if (innerCosClient == defaultClient) {
            if (defaultTransferManager == null) {
                defaultTransferManager = createTransferManager(defaultClient)
            }
            defaultTransferManager!!
        } else {
            createTransferManager(innerCosClient)
        }
    }

    private fun createTransferManager(innerCosClient: InnerCosClient): TransferManager {
        val executorService = (taskAsyncExecutor as ThreadPoolTaskExecutor).threadPoolExecutor
        val transferManager = TransferManager(innerCosClient.cosClient, executorService, false)
        transferManager.configuration = TransferManagerConfiguration().apply {
            multipartUploadThreshold = 10L * MB
            minimumUploadPartSize = 5L * MB
        }
        return transferManager
    }

    private fun shutdownTransferManager(transferManager: TransferManager) {
        if (transferManager != defaultTransferManager) {
            transferManager.shutdownNow(true)
        }
    }
}
