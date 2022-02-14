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

package com.tencent.bkrepo.scanner.service.impl

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.scanner.dao.ScanTaskDao
import com.tencent.bkrepo.scanner.dao.ScannerDao
import com.tencent.bkrepo.scanner.exception.ScanTaskNotFoundException
import com.tencent.bkrepo.scanner.exception.ScannerNotFoundException
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.pojo.ScanRequest
import com.tencent.bkrepo.scanner.pojo.ScanResultOverview
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import com.tencent.bkrepo.scanner.pojo.ScanTriggerType
import com.tencent.bkrepo.scanner.service.ScanService
import com.tencent.bkrepo.scanner.task.ScanTaskScheduler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

@Service
class ScanServiceImpl @Autowired constructor(
    private val scanTaskDao: ScanTaskDao,
    private val scannerDao: ScannerDao,
    private val scanTaskScheduler: ScanTaskScheduler
) : ScanService {

    @Transactional(rollbackFor = [Throwable::class])
    override fun scan(scanRequest: ScanRequest, triggerType: ScanTriggerType): ScanTask {
        if (scannerDao.existsByName(scanRequest.scanner)) {
            throw ScannerNotFoundException(scanRequest.scanner)
        }
        val now = LocalDateTime.now()
        val scanTask = scanTaskDao.save(
            TScanTask(
                createdBy = "",
                createdDate = now,
                lastModifiedBy = "",
                lastModifiedDate = now,
                rule = scanRequest.rule.toJsonString(),
                triggerType = triggerType.name,
                status = ScanTaskStatus.PENDING.name,
                total = 0L,
                scanned = 0L,
                scannerKey = scanRequest.scanner,
                scanResultOverview = ScanResultOverview()
            )
        ).run { convert(this) }

        scanTaskScheduler.schedule(scanTask)
        return scanTask
    }

    override fun task(taskId: String): ScanTask {
        return scanTaskDao.findById(taskId)?.let {
            convert(it)
        } ?: throw ScanTaskNotFoundException(taskId)
    }

    private fun convert(scanTask: TScanTask): ScanTask = with(scanTask) {
        ScanTask(
            id!!,
            createdBy,
            createdDate.format(ISO_DATE_TIME),
            startDateTime?.format(ISO_DATE_TIME),
            finishedDateTime?.format(ISO_DATE_TIME),
            status,
            total,
            scanned,
            scannerKey,
            scanResultOverview
        )
    }
}
