/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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
package com.tencent.bkrepo.generic.service.imp

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.operate.service.model.TOperateLog
import com.tencent.bkrepo.generic.dao.GenericDownloadRecordDao
import com.tencent.bkrepo.generic.model.GenericPageRequest
import com.tencent.bkrepo.generic.service.GenericDownloadRecordService
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class GenericDownloadRecordServiceImp(
    private val genericDownloadRecordDao: GenericDownloadRecordDao
): GenericDownloadRecordService {

    override fun getRecord(genericPageRequest: GenericPageRequest): Page<TOperateLog> {
        with(genericPageRequest) {
            val query = Query()
            query.addCriteria(Criteria.where("type").`is`(EventType.NODE_DOWNLOADED.name))
            query.addCriteria(Criteria.where("projectId").`is`(projectId))
            query.addCriteria(Criteria.where("repoName").`is`(repoName))
            query.addCriteria(Criteria.where("resourceKey").`is`(path))
            query.addCriteria(Criteria.where("createdDate")
                .lte(LocalDateTime.now())
                .gte(LocalDateTime.now().minusYears(1))
            )
            query.with(Sort.by(Sort.Order(Sort.Direction.DESC,TOperateLog::createdDate.name)))
            val totalRecords = genericDownloadRecordDao.count(query)
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val records = genericDownloadRecordDao.find(query.with(pageRequest))
            return Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }


}