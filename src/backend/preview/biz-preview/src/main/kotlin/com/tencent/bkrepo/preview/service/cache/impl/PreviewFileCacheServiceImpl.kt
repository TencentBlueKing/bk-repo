/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.preview.service.cache.impl

import com.tencent.bkrepo.preview.dao.FilePreviewCacheDao
import com.tencent.bkrepo.preview.model.TPreviewFileCache
import com.tencent.bkrepo.preview.pojo.cache.PreviewFileCacheCreateRequest
import com.tencent.bkrepo.preview.pojo.cache.PreviewFileCacheInfo
import com.tencent.bkrepo.preview.service.cache.PreviewFileCacheService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PreviewFileCacheServiceImpl(
    private val filePreviewCacheDao: FilePreviewCacheDao,
) : PreviewFileCacheService {
    override fun createCache(requestFile: PreviewFileCacheCreateRequest): PreviewFileCacheInfo {
        var filePreviewCache = TPreviewFileCache(
            id = null,
            md5 = requestFile.md5,
            projectId = requestFile.projectId,
            repoName = requestFile.repoName,
            fullPath = requestFile.fullPath,
            createdDate = requestFile.createdDate?: LocalDateTime.now()
        )
        filePreviewCacheDao.insert(filePreviewCache)
        return convert(filePreviewCache)
    }

    override fun removeCache(md5: String, projectId: String, repoName: String) {
        filePreviewCacheDao.removeCache(md5, projectId, repoName)
    }

    override fun getCache(md5: String, projectId: String, repoName: String): PreviewFileCacheInfo? {
        return filePreviewCacheDao.getCache(md5, projectId, repoName)?.let { convert(it) }
    }

    private fun convert(previewFileCache: TPreviewFileCache): PreviewFileCacheInfo {
        return previewFileCache.let {
            PreviewFileCacheInfo(
                md5 = it.md5,
                projectId = it.projectId,
                repoName = it.repoName,
                fullPath = it.fullPath,
                createdDate = it.createdDate
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PreviewFileCacheServiceImpl::class.java)
    }
}