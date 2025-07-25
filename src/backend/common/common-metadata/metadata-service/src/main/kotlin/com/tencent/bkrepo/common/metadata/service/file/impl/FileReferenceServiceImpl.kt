/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.file.impl

import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.file.FileReferenceDao
import com.tencent.bkrepo.common.metadata.model.TFileReference
import com.tencent.bkrepo.common.metadata.pojo.file.FileReference
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.util.FileReferenceQueryHelper.buildQuery
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

/**
 * 文件引用服务实现类
 */
@Service
@Conditional(SyncCondition::class)
class FileReferenceServiceImpl(
    private val fileReferenceDao: FileReferenceDao,
) : FileReferenceService {

    override fun increment(sha256: String, credentialsKey: String?, inc: Long): Boolean {
        val query = buildQuery(sha256, credentialsKey)
        val update = Update().inc(TFileReference::count.name, inc)
        try {
            fileReferenceDao.upsert(query, update)
        } catch (exception: DuplicateKeyException) {
            // retry because upsert operation is not atomic
            fileReferenceDao.upsert(query, update)
        }
        logger.info("Increment reference of file [$sha256] on credentialsKey [$credentialsKey].")
        return true
    }

    override fun decrement(sha256: String, credentialsKey: String?): Boolean {
        val query = buildQuery(sha256, credentialsKey, 0)
        val update = Update().apply { inc(TFileReference::count.name, -1) }
        val result = fileReferenceDao.updateFirst(query, update)

        if (result.modifiedCount == 1L) {
            logger.info("Decrement references of file [$sha256] on credentialsKey [$credentialsKey].")
            return true
        }

        fileReferenceDao.findOne(buildQuery(sha256, credentialsKey)) ?: run {
            logger.error("Failed to decrement reference of file [$sha256] on credentialsKey [$credentialsKey]")
            return false
        }

        logger.error(
            "Failed to decrement reference of file [$sha256] on credentialsKey [$credentialsKey]: " +
                "reference count is 0."
        )
        return false
    }

    override fun count(sha256: String, credentialsKey: String?): Long {
        val query = buildQuery(sha256, credentialsKey)
        return fileReferenceDao.findOne(query)?.count ?: 0
    }

    override fun get(credentialsKey: String?, sha256: String): FileReference {
        val query = buildQuery(sha256, credentialsKey)
        val tFileReference = fileReferenceDao.findOne(query)
            ?: throw NotFoundException(ArtifactMessageCode.NODE_NOT_FOUND)
        return convert(tFileReference)
    }

    override fun exists(sha256: String, credentialsKey: String?): Boolean {
        return fileReferenceDao.exists(buildQuery(sha256, credentialsKey))
    }

    private fun convert(tFileReference: TFileReference): FileReference {
        return tFileReference.run { FileReference(sha256, credentialsKey, count) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileReferenceServiceImpl::class.java)
    }
}
