package com.tencent.bkrepo.common.storage.innercos

import com.qcloud.s1.cos.COSClient
import com.qcloud.s1.cos.ClientConfig
import com.qcloud.s1.cos.auth.BasicCOSCredentials
import com.qcloud.s1.cos.exception.CosServiceException
import com.qcloud.s1.cos.model.DeleteObjectRequest
import com.qcloud.s1.cos.model.GetObjectRequest
import com.qcloud.s1.cos.model.ObjectMetadata
import com.qcloud.s1.cos.model.PutObjectRequest
import com.qcloud.s1.cos.region.Region
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import org.apache.http.HttpStatus
import java.io.InputStream

/**
 * tencent inner cos 文件存储实现类
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class InnerCosFileStorage(
        locateStrategy: LocateStrategy,
        defaultCredentials: InnerCosCredentials
) : AbstractFileStorage<InnerCosCredentials, InnerCosClient>(locateStrategy, defaultCredentials) {

    override fun createClient(credentials: InnerCosCredentials): InnerCosClient {
        val basicCOSCredentials = BasicCOSCredentials(credentials.appId, credentials.secretId, credentials.secretKey)
        val clientConfig = ClientConfig(Region(credentials.regionName))
        clientConfig.host = credentials.host
        return InnerCosClient(credentials.bucketName, COSClient(basicCOSCredentials, clientConfig))
    }

    override fun store(path: String, filename: String, inputStream: InputStream, client: InnerCosClient) {
        val objectMetadata = ObjectMetadata()
        objectMetadata.contentLength = inputStream.available().toLong()
        val putObjectRequest = PutObjectRequest(client.bucketName, filename, inputStream, objectMetadata)
        client.cosClient.putObject(putObjectRequest)
    }

    override fun delete(path: String, filename: String, client: InnerCosClient) {
        val deleteObjectRequest = DeleteObjectRequest(client.bucketName, filename)
        client.cosClient.deleteObject(deleteObjectRequest)
    }

    override fun load(path: String, filename: String, client: InnerCosClient): InputStream? {
        return if(exist(path, filename, client)) {
            val getObjectRequest = GetObjectRequest(client.bucketName, filename)
            client.cosClient.getObject(getObjectRequest).objectContent
        } else null

    }

    override fun exist(path: String, filename: String, client: InnerCosClient): Boolean {
        var exists = true
        try {
            client.cosClient.getobjectMetadata(client.bucketName, filename)
        } catch (cosServiceException: CosServiceException) {
            exists = cosServiceException.statusCode != HttpStatus.SC_NOT_FOUND
        }
        return exists
    }

}