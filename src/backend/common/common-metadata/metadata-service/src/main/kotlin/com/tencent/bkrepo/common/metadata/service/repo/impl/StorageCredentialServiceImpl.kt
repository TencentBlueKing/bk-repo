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

package com.tencent.bkrepo.common.metadata.service.repo.impl

import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.dao.repo.StorageCredentialsDao
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper.Companion.buildStorageCredential
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper.Companion.checkCreateRequest
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper.Companion.convert
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.HDFSCredentials
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.S3Credentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsCreateRequest
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsUpdateRequest
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

/**
 * 存储凭证服务实现类
 */
@Service
@Conditional(SyncCondition::class)
class StorageCredentialServiceImpl(
    private val repositoryDao: RepositoryDao,
    private val storageCredentialsDao: StorageCredentialsDao,
    private val storageProperties: StorageProperties,
) : StorageCredentialService {

    @Transactional(rollbackFor = [Throwable::class])
    override fun create(userId: String, request: StorageCredentialsCreateRequest): StorageCredentials {
        checkCreateRequest(request)
        storageCredentialsDao.findById(request.key)?.run {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, request.key)
        }
        val storageCredential = buildStorageCredential(request, userId)
        val savedCredentials = storageCredentialsDao.save(storageCredential)
        return convert(savedCredentials)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun update(userId: String, request: StorageCredentialsUpdateRequest): StorageCredentials {
        requireNotNull(request.key)
        val tStorageCredentials = storageCredentialsDao.findById(request.key!!)
            ?: throw NotFoundException(RepositoryMessageCode.STORAGE_CREDENTIALS_NOT_FOUND)
        StorageCredentialHelper.updateCredentials(tStorageCredentials, request)
        val updatedCredentials = storageCredentialsDao.save(tStorageCredentials)
        return convert(updatedCredentials)
    }

    override fun findByKey(key: String?): StorageCredentials? {
        return if (key.isNullOrBlank()) {
            storageProperties.defaultStorageCredentials()
        } else {
            storageCredentialsDao.findById(key)?.let { convert(it) }
        }
    }

    override fun list(region: String?): List<StorageCredentials> {
        return storageCredentialsDao.findAll()
            .filter { region.isNullOrBlank() || it.region == region }
            .map { convert(it) }
    }

    override fun default(): StorageCredentials {
        return storageProperties.defaultStorageCredentials()
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun delete(key: String) {
        if (!storageCredentialsDao.existsById(key)) {
            throw NotFoundException(RepositoryMessageCode.STORAGE_CREDENTIALS_NOT_FOUND)
        }
        val credentialsCount = storageCredentialsDao.count()
        if (repositoryDao.existsByCredentialsKey(key) || credentialsCount <= 1) {
            throw BadRequestException(RepositoryMessageCode.STORAGE_CREDENTIALS_IN_USE)
        }
        // 可能判断完凭证未被使用后，删除凭证前，又有新增的仓库使用凭证，出现这种情况后需要修改新增仓库的凭证
        return storageCredentialsDao.deleteById(key)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun forceDelete(key: String) {
        return storageCredentialsDao.deleteById(key)
    }

    override fun getStorageKeyMapping(): Map<String, Set<String>> {
        val mappingKeys = ConcurrentHashMap<String, MutableSet<String>>()
        val credentials = list() + default()
        credentials.forEach { credential1 ->
            val keys = mappingKeys.getOrPut(credential1.key ?: DEFAULT_STORAGE_KEY) { ConcurrentHashSet() }
            credentials.forEach { credential2 ->
                if (credential1.key != credential2.key && sameStorage(credential1, credential2)) {
                    keys.add(credential2.key ?: DEFAULT_STORAGE_KEY)
                }
            }
        }
        return mappingKeys
    }

    /**
     * 判断使用的后端存储是否相同
     */
    private fun sameStorage(credential1: StorageCredentials, credential2: StorageCredentials): Boolean {
        if (credential1::class.java != credential2::class.java) {
            return false
        }

        if (credential1 is InnerCosCredentials && credential2 is InnerCosCredentials) {
            return credential1.bucket == credential2.bucket
        }

        if (credential1 is S3Credentials && credential2 is S3Credentials) {
            return credential1.bucket == credential2.bucket
        }

        if (credential1 is FileSystemCredentials && credential2 is FileSystemCredentials) {
            return credential1.path == credential2.path
        }

        if (credential1 is HDFSCredentials && credential2 is HDFSCredentials) {
            throw RuntimeException("Unsupported credentials type[HDFSCredentials]")
        }

        return false
    }
}
