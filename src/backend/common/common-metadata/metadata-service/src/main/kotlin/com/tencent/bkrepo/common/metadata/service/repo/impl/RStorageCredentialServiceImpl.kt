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

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.dao.repo.RRepositoryDao
import com.tencent.bkrepo.common.metadata.dao.repo.RStorageCredentialsDao
import com.tencent.bkrepo.common.metadata.service.repo.RStorageCredentialService
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper.Companion.buildStorageCredential
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper.Companion.checkCreateRequest
import com.tencent.bkrepo.common.metadata.util.StorageCredentialHelper.Companion.convert
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsCreateRequest
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsUpdateRequest
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 存储凭证服务实现类
 */
@Service
@Conditional(ReactiveCondition::class)
class RStorageCredentialServiceImpl(
    private val repositoryDao: RRepositoryDao,
    private val storageCredentialsDao: RStorageCredentialsDao,
    private val storageProperties: StorageProperties,
) : RStorageCredentialService {

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun create(userId: String, request: StorageCredentialsCreateRequest): StorageCredentials {
        checkCreateRequest(request)
        storageCredentialsDao.findById(request.key)?.run {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, request.key)
        }
        val storageCredential = buildStorageCredential(request, userId)
        val savedCredentials = storageCredentialsDao.save(storageCredential)
        return convert(savedCredentials)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun update(userId: String, request: StorageCredentialsUpdateRequest): StorageCredentials {
        requireNotNull(request.key)
        val tStorageCredentials = storageCredentialsDao.findById(request.key!!)
            ?: throw NotFoundException(RepositoryMessageCode.STORAGE_CREDENTIALS_NOT_FOUND)
        StorageCredentialHelper.updateCredentials(tStorageCredentials, request)
        val updatedCredentials = storageCredentialsDao.save(tStorageCredentials)
        return convert(updatedCredentials)
    }

    override suspend fun findByKey(key: String?): StorageCredentials? {
        return if (key.isNullOrBlank()) {
            storageProperties.defaultStorageCredentials()
        } else {
            storageCredentialsDao.findById(key)?.let { convert(it) }
        }
    }

    override suspend fun list(region: String?): List<StorageCredentials> {
        return storageCredentialsDao.findAll()
            .filter { region.isNullOrBlank() || it.region == region }
            .map { convert(it) }
    }

    override suspend fun default(): StorageCredentials {
        return storageProperties.defaultStorageCredentials()
    }

    @Transactional(rollbackFor = [Throwable::class])
    override suspend fun delete(key: String) {
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
    override suspend fun forceDelete(key: String) {
        return storageCredentialsDao.deleteById(key)
    }
}
