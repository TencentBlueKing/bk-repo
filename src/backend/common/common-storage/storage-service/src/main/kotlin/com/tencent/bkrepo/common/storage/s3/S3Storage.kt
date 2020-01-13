package com.tencent.bkrepo.common.storage.s3

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.DeleteObjectRequest
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.S3Credentials
import java.io.File

/**
 *
 * @author: carrypan
 * @date: 2020/1/13
 */
open class S3Storage : AbstractFileStorage<S3Credentials, S3Client>() {

    override fun store(path: String, filename: String, file: File, client: S3Client) {
        val transferManager = TransferManagerBuilder.standard()
            .withS3Client(client.s3Client)
            .build()
        val upload = transferManager.upload(client.bucketName, filename, file)
        upload.waitForCompletion()
        transferManager.shutdownNow()
        transferManager.shutdownNow()
    }

    override fun load(path: String, filename: String, received: File, client: S3Client): File? {
        val transferManager = TransferManagerBuilder.standard()
            .withS3Client(client.s3Client)
            .build()
        val getObjectRequest = GetObjectRequest(client.bucketName, filename)
        val download = transferManager.download(getObjectRequest, received)
        download.waitForCompletion()
        transferManager.shutdownNow()
        return received
    }

    override fun delete(path: String, filename: String, client: S3Client) {
        val deleteObjectRequest = DeleteObjectRequest(client.bucketName, filename)
        client.s3Client.deleteObject(deleteObjectRequest)
    }

    override fun exist(path: String, filename: String, client: S3Client): Boolean {
        return try{
            client.s3Client.doesObjectExist(client.bucketName, filename)
        } catch (exception: Exception) {
            false
        }
    }

    override fun onCreateClient(credentials: S3Credentials): S3Client {
        require(credentials.accessKey.isNotBlank())
        require(credentials.secretKey.isNotBlank())
        require(credentials.endpoint.isNotBlank())
        require(credentials.region.isNotBlank())
        require(credentials.bucket.isNotBlank())

        val config = ClientConfiguration()
        val endpointConfig = EndpointConfiguration(credentials.endpoint, credentials.region)
        val awsCredentials = BasicAWSCredentials(credentials.accessKey, credentials.secretKey)
        val awsCredentialsProvider = AWSStaticCredentialsProvider(awsCredentials)

        val amazonS3 = AmazonS3Client.builder()
            .withEndpointConfiguration(endpointConfig)
            .withClientConfiguration(config)
            .withCredentials(awsCredentialsProvider)
            .disableChunkedEncoding()
            .withPathStyleAccessEnabled(true)
            .build()

        return S3Client(credentials.bucket, amazonS3)

    }

    override fun getDefaultCredentials() = storageProperties.s3
}