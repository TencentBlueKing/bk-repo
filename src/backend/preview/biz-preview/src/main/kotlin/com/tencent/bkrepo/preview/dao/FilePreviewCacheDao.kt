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

package com.tencent.bkrepo.preview.dao

import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.preview.model.TPreviewFileCache
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class FilePreviewCacheDao : SimpleMongoDao<TPreviewFileCache>() {
    /**
     * 查找缓存
     */
    fun getCache(md5: String, projectId: String, repoName: String): TPreviewFileCache? {
        return this.findOne(Query(buildCriteria(md5, projectId, repoName)))
    }
    /**
     * 删除缓存
     */
    fun removeCache(md5: String, projectId: String, repoName: String): DeleteResult {
        return this.remove(Query(buildCriteria(md5, projectId, repoName)))
    }

    private fun buildCriteria(md5: String, projectId: String, repoName: String): Criteria {
        return Criteria
            .where(TPreviewFileCache::md5.name).isEqualTo(md5)
            .and(TPreviewFileCache::projectId.name).isEqualTo(projectId)
            .and(TPreviewFileCache::repoName.name).isEqualTo(repoName)
    }
}