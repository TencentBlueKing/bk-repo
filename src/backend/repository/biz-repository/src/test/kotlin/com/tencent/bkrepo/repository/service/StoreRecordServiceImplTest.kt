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

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import com.tencent.bkrepo.repository.dao.StoreRecordDao
import com.tencent.bkrepo.repository.model.TStoreRecord
import com.tencent.bkrepo.repository.service.file.StoreRecordService
import com.tencent.bkrepo.repository.service.file.impl.StoreRecordServiceImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo

@DisplayName("存储记录测试")
@DataMongoTest
@Import(StoreRecordServiceImpl::class, StoreRecordDao::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreRecordServiceImplTest @Autowired constructor(
    private val storeRecordService: StoreRecordService,
    private val storeRecordDao: StoreRecordDao,
) {
    @Test
    fun test() {
        val sha256 = uniqueId()
        val criteria = TStoreRecord::sha256.isEqualTo(sha256).and(TStoreRecord::credentialsKey.name).isEqualTo(null)
        val query = Query(criteria)
        assertFalse(storeRecordDao.exists(query))

        // record storing
        val record = storeRecordService.recordStoring(sha256, null)
        assertTrue(storeRecordDao.exists(query))

        // store finished
        storeRecordService.storeFinished(record.id)
        assertFalse(storeRecordDao.exists(query))
    }
}
