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

package com.tencent.bkrepo.job.repository

import com.tencent.bkrepo.common.mongo.dao.sharding.HashShardingMongoDao
import com.tencent.bkrepo.job.pojo.TFolderSizeStat
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class FolderStatRepository : HashShardingMongoDao<TFolderSizeStat>() {


    /**
     * 统计前插入数据，标记开始进行统计
     */
    fun initFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String
    ) {
        updateFolderSize(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath
        )
    }

    /**
     * 统计完成更新数据
     */
    fun upsertFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long
    ) {
        updateFolderSize(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            size = size,
            finish = true
        )
    }

    private fun updateFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long = 0,
        finish: Boolean = false
    ) {
        val query = Query(
            Criteria.where(TFolderSizeStat::projectId.name).isEqualTo(projectId)
                .and(TFolderSizeStat::repoName.name).isEqualTo(repoName)
                .and(TFolderSizeStat::folderPath.name).isEqualTo(fullPath)
        )
        val (update, options) = if (!finish) {
            // 在统计前新增的节点需要清理
            val update = Update().set(TFolderSizeStat::size.name, size)
                .set(TFolderSizeStat::createdDate.name, LocalDateTime.now())
                .set(TFolderSizeStat::lastModifiedDate.name, LocalDateTime.now())
            // 设置为true, 是可能当还没有进行统计前，监听事件进行了创建，这时需要清理，后续统计的会包含该数据
            val options = FindAndModifyOptions().upsert(true)
            Pair(update, options)
        } else {
            // 在统计中新增的节点是不会加到统计结果中，需加入
            val update = Update().inc(TFolderSizeStat::size.name, size)
                .set(TFolderSizeStat::lastModifiedDate.name, LocalDateTime.now())
            // 设置为false, 正常情况下会存在数据，只需进行更新，但是可能存在目录被删，导致数据不存在，这时就不需要将统计结果进行更新
            val options = FindAndModifyOptions().upsert(false)
            Pair(update, options)
        }
        this.determineMongoTemplate().findAndModify(query, update, options, TFolderSizeStat::class.java)
    }
}