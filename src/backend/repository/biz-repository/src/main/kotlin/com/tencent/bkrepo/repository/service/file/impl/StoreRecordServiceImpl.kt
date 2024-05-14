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

package com.tencent.bkrepo.repository.service.file.impl

import com.tencent.bkrepo.fs.server.constant.ID
import com.tencent.bkrepo.repository.dao.StoreRecordDao
import com.tencent.bkrepo.repository.model.TStoreRecord
import com.tencent.bkrepo.repository.pojo.file.StoreRecord
import com.tencent.bkrepo.repository.service.file.StoreRecordService
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class StoreRecordServiceImpl(
    private val storeRecordDao: StoreRecordDao
) : StoreRecordService {
    override fun recordStoring(sha256: String, credentialsKey: String?): StoreRecord {
        val now = LocalDateTime.now()
        val record = storeRecordDao.insert(
            TStoreRecord(
                id = null,
                createdDate = now,
                lastModifiedDate = now,
                sha256 = sha256,
                credentialsKey = credentialsKey
            )
        )
        return convert(record)
    }

    override fun storeFinished(id: String): Boolean {
        return storeRecordDao.remove(Query(Criteria.where(ID).isEqualTo(id))).deletedCount == 1L
    }

    private fun convert(record: TStoreRecord) = with(record) {
        StoreRecord(
            id = id!!,
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate,
            sha256 = sha256,
            credentialsKey = credentialsKey
        )
    }
}
