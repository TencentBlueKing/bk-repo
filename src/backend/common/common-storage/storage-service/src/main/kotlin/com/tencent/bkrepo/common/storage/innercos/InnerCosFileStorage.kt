/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.innercos

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.AbstractEncryptorFileStorage
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.request.CheckObjectExistRequest
import com.tencent.bkrepo.common.storage.innercos.request.CopyObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.DeleteObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.ListObjectsRequest
import com.tencent.bkrepo.common.storage.innercos.request.MigrateObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.RestoreObjectRequest
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream

/**
 * 内部cos文件存储实现类
 */
open class InnerCosFileStorage : AbstractEncryptorFileStorage<InnerCosCredentials, CosClient>() {

    override fun store(path: String, name: String, file: File, client: CosClient, storageClass: String?) {
        client.putFileObject(name, file, storageClass)
    }

    override fun store(path: String, name: String, inputStream: InputStream, size: Long, client: CosClient) {
        client.putStreamObject(name, inputStream, size)
    }

    override fun load(path: String, name: String, range: Range, client: CosClient): InputStream? {
        val request = if (range == Range.FULL_RANGE) {
            GetObjectRequest(name)
        } else {
            // 确定范围可以使用并发下载
            GetObjectRequest(name, range.start, range.end)
        }
        return client.getObjectByChunked(request).inputStream
    }

    override fun delete(path: String, name: String, client: CosClient) {
        return try {
            client.deleteObject(DeleteObjectRequest(name))
        } catch (ignored: IOException) {
            logger.error("delete obj[$name] from ${client.credentials.key} failed", ignored)
        }
    }

    override fun exist(path: String, name: String, client: CosClient): Boolean {
        return try {
            return client.checkObjectExist(CheckObjectExistRequest(name))
        } catch (ignored: IOException) {
            // return false if error
            logger.error("check file[$path/$name] exists in cos failed", ignored)
            false
        }
    }

    override fun copy(
        fromPath: String,
        fromName: String,
        toPath: String,
        toName: String,
        fromClient: CosClient,
        toClient: CosClient
    ) {
        val fromCredentials = fromClient.credentials
        val toCredentials = toClient.credentials
        val sameStorage = fromCredentials.public == toCredentials.public &&
                fromCredentials.inner == toCredentials.inner &&
                fromCredentials.region == toCredentials.region &&
                fromCredentials.bucket == toCredentials.bucket
        if (sameStorage) {
            toClient.copyObject(CopyObjectRequest(fromCredentials.bucket, fromName, toName))
        } else {
            toClient.migrateObject(MigrateObjectRequest(fromClient, fromName, toName))
        }
    }

    override fun move(
        fromPath: String,
        fromName: String,
        toPath: String,
        toName: String,
        fromClient: CosClient,
        toClient: CosClient
    ) {
        copy(fromPath, fromName, toPath, toName, fromClient, toClient)
        delete(fromPath, fromName, fromClient)
    }

    override fun checkRestore(path: String, name: String, client: CosClient): Boolean {
        val checkObjectExistRequest = CheckObjectExistRequest(name)
        return client.checkObjectRestore(checkObjectExistRequest)
    }

    override fun restore(path: String, name: String, days: Int, tier: String, client: CosClient) {
        val restoreRequest = RestoreObjectRequest(name, days, tier)
        client.restoreObject(restoreRequest)
    }

    override fun listAll(path: String, client: CosClient): Stream<Path> {
        val keyPrefix = if (path == StringPool.ROOT) null else path
        val listObjectsRequest = ListObjectsRequest(prefix = keyPrefix)
        return client.listObjects(listObjectsRequest).map { Paths.get(it) }
    }

    override fun onCreateClient(credentials: InnerCosCredentials): CosClient {
        require(credentials.secretId.isNotBlank())
        require(credentials.secretKey.isNotBlank())
        require(credentials.region.isNotBlank())
        require(credentials.bucket.isNotBlank())
        return CosClient(credentials)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InnerCosFileStorage::class.java)
    }
}
