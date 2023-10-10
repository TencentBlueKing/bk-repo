/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch.utils

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.job.pojo.FolderInfo

object FolderUtils {


    /**
     * 生成缓存key
     */
    fun buildCacheKey(
        projectId: String,
        repoName: String? = null,
        fullPath: String? = null,
        collectionName: String? = null,
        tag: String? = null,
    ): String {
        return StringBuilder().apply {
            collectionName?.let {
                this.append(it).append(StringPool.COLON)
            }
            this.append(projectId)
            repoName?.let {
                this.append(StringPool.COLON).append(repoName)
            }
            fullPath?.let {
                this.append(StringPool.COLON).append(fullPath)
            }
            tag?.let {
                this.append(StringPool.COLON).append(tag)
            }
        }.toString()
    }

    /**
     * 从缓存key中解析出节点信息
     */
    fun extractFolderInfoFromCacheKey(key: String): FolderInfo? {
        val values = key.split(StringPool.COLON)
        return try {
            FolderInfo(
                projectId = values[1],
                repoName = values[2],
                fullPath = values[3]
            )
        } catch (e: Exception) {
            null
        }
    }

}