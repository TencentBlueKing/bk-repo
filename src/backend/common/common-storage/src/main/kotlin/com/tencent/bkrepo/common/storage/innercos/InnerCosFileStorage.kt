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
import com.tencent.bkrepo.common.storage.AbstractFileStorage
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import org.apache.http.HttpStatus
import java.io.InputStream

/**
 * tencent inner cos 文件存储实现类
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
class InnerCosFileStorage(locateStrategy: LocateStrategy, innerCosProperties: InnerCosProperties) : AbstractFileStorage(locateStrategy) {

    private val cosClient: COSClient
    private val bucketName: String = innerCosProperties.bucket

    init {
        val credentials = BasicCOSCredentials(innerCosProperties.appId, innerCosProperties.secretId, innerCosProperties.secretKey)
        val clientConfig = ClientConfig(Region(innerCosProperties.region))
        clientConfig.host = innerCosProperties.host
        cosClient = COSClient(credentials, clientConfig)
    }

    override fun store(path: String, filename: String, inputStream: InputStream) {
        val objectMetadata = ObjectMetadata()
        objectMetadata.contentLength = inputStream.available().toLong()
        val putObjectRequest = PutObjectRequest(bucketName, filename, inputStream, objectMetadata)
        cosClient.putObject(putObjectRequest)
    }

    override fun delete(path: String, filename: String) {
        val deleteObjectRequest = DeleteObjectRequest(bucketName, filename)
        cosClient.deleteObject(deleteObjectRequest)
    }

    override fun load(path: String, filename: String): InputStream {
        val getObjectRequest = GetObjectRequest(bucketName, filename)
        return cosClient.getObject(getObjectRequest).objectContent
    }

    override fun exist(path: String, filename: String): Boolean {
        var exists = true
        try {
            cosClient.getobjectMetadata(bucketName, filename)
        } catch (cosServiceException: CosServiceException) {
            exists = cosServiceException.statusCode != HttpStatus.SC_NOT_FOUND
        }
        return exists
    }


}