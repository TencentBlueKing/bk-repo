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

@file:Suppress("DEPRECATION")

package com.tencent.bkrepo.scanner.utils

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.scanner.pojo.scanner.Level
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.normalizedLevel
import com.tencent.bkrepo.scanner.model.SubScanTaskDefinition
import com.tencent.bkrepo.scanner.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.scanner.model.TScanPlan
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.pojo.LeakType
import com.tencent.bkrepo.scanner.pojo.ScanPlan
import com.tencent.bkrepo.scanner.pojo.ScanStatus
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import com.tencent.bkrepo.scanner.pojo.request.CreateScanPlanRequest
import com.tencent.bkrepo.scanner.pojo.request.PlanCountRequest
import com.tencent.bkrepo.scanner.pojo.request.SubtaskInfoRequest
import com.tencent.bkrepo.scanner.pojo.request.UpdateScanPlanRequest
import com.tencent.bkrepo.scanner.pojo.response.ArtifactPlanRelation
import com.tencent.bkrepo.scanner.pojo.response.ScanPlanInfo
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties

object ScanPlanConverter {
    fun convert(scanPlan: TScanPlan): ScanPlan {
        return with(scanPlan) {
            ScanPlan(
                id = id!!,
                projectId = projectId,
                name = name,
                type = type,
                scanTypes = scanTypes,
                scanner = scanner,
                description = description,
                scanOnNewArtifact = scanOnNewArtifact,
                repoNames = repoNames,
                rule = rule.readJsonString(),
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                scanQuality = scanQuality,
                readOnly = readOnly
            )
        }
    }

    fun convert(scanPlanRequest: UpdateScanPlanRequest): ScanPlan {
        return with(scanPlanRequest) {
            val (repoNames, filterRule) = if (scanOnNewArtifact != null && scanOnNewArtifact!!) {
                Pair(RuleUtil.getRepoNames(rule), rule)
            } else {
                Pair(null, null)
            }
            ScanPlan(
                id = id,
                projectId = projectId,
                name = name,
                description = description,
                scanOnNewArtifact = scanOnNewArtifact,
                repoNames = repoNames,
                rule = filterRule
            )
        }
    }

    fun convert(scanPlanRequest: CreateScanPlanRequest): ScanPlan {
        return with(scanPlanRequest) {
            ScanPlan(
                projectId = projectId,
                name = name,
                type = type,
                scanTypes = scanTypes,
                scanner = scanner,
                description = description,
                scanOnNewArtifact = autoScan,
                repoNames = emptyList(),
                rule = RuleConverter.convert(projectId, emptyList(), type)
            )
        }
    }

    fun convert(scanPlan: TScanPlan, subScanTasks: List<TPlanArtifactLatestSubScanTask>): ScanPlanInfo {
        with(scanPlan) {
            var critical = 0L
            var high = 0L
            var medium = 0L
            var low = 0L
            subScanTasks.forEach { subScanTask ->
                critical += Converter.getCveCount(Level.CRITICAL.levelName, subScanTask)
                high += Converter.getCveCount(Level.HIGH.levelName, subScanTask)
                medium += Converter.getCveCount(Level.MEDIUM.levelName, subScanTask)
                low += Converter.getCveCount(Level.LOW.levelName, subScanTask)
            }

            return ScanPlanInfo(
                id = id!!,
                name = name,
                planType = type,
                scanTypes = scanTypes,
                projectId = projectId,
                status = "",
                artifactCount = subScanTasks.size.toLong(),
                critical = critical,
                high = high,
                medium = medium,
                low = low,
                total = critical + high + medium + low,
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastScanDate = null,
                readOnly = readOnly
            )
        }
    }

    fun convert(scanPlan: TScanPlan, latestScanTask: TScanTask?, artifactCount: Long): ScanPlanInfo {
        with(scanPlan) {
            val scannerType = latestScanTask?.scannerType
            val overview = scanPlan.scanResultOverview

            val critical = Converter.getCveCount(scannerType, Level.CRITICAL.levelName, overview)
            val high = Converter.getCveCount(scannerType, Level.HIGH.levelName, overview)
            val medium = Converter.getCveCount(scannerType, Level.MEDIUM.levelName, overview)
            val low = Converter.getCveCount(scannerType, Level.LOW.levelName, overview)
            val status = latestScanTask?.let { convertToScanStatus(it.status).name } ?: ScanStatus.INIT.name

            return ScanPlanInfo(
                id = id!!,
                name = name,
                planType = type,
                scanTypes = scanTypes,
                projectId = projectId,
                status = status,
                artifactCount = artifactCount,
                critical = critical,
                high = high,
                medium = medium,
                low = low,
                total = critical + high + medium + low,
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastScanDate = latestScanTask?.startDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
                readOnly = readOnly
            )
        }
    }

    fun convert(request: PlanCountRequest): PlanCountRequest {
        request.startDateTime = request.startTime?.let { LocalDateTime.ofInstant(it, ZoneOffset.systemDefault()) }
        request.endDateTime = request.endTime?.let { LocalDateTime.ofInstant(it, ZoneOffset.systemDefault()) }
        return request
    }

    fun convert(request: SubtaskInfoRequest): SubtaskInfoRequest {
        request.highestLeakLevel = request.highestLeakLevel?.let { normalizedLevel(it) }
        request.startDateTime = request.startTime?.let { LocalDateTime.ofInstant(it, ZoneOffset.systemDefault()) }
        request.endDateTime = request.endTime?.let { LocalDateTime.ofInstant(it, ZoneOffset.systemDefault()) }
        if (!request.status.isNullOrEmpty()) {
            request.subScanTaskStatus = convertToSubScanTaskStatus(ScanStatus.valueOf(request.status!!)).map { it.name }
        }
        return request
    }

    fun duration(startDateTime: LocalDateTime?, finishedDateTime: LocalDateTime?): Long {
        return if (startDateTime != null && finishedDateTime != null) {
            Duration.between(startDateTime, finishedDateTime).toMillis()
        } else {
            0L
        }
    }

    fun convertToArtifactPlanRelation(subScanTask: SubScanTaskDefinition, planName: String): ArtifactPlanRelation {
        val planType = subScanTask.repoType
        return with(subScanTask) {
            ArtifactPlanRelation(
                id = planId ?: "",
                planId = planId ?: "",
                projectId = projectId,
                planType = planType,
                name = planName,
                status = convertToScanStatus(status).name,
                recordId = id!!,
                subTaskId = id!!
            )
        }
    }

    fun artifactStatus(status: List<String>): String {
        require(status.isNotEmpty())
        var maxStatus: ScanStatus? = null
        status.forEach { curStatus ->
            if (curStatus == ScanStatus.RUNNING.name) {
                return curStatus
            }
            maxStatus = maxStatus
                ?.let { max -> maxOf(ScanStatus.valueOf(curStatus), max) }
                ?: ScanStatus.valueOf(curStatus)
        }
        return maxStatus!!.name
    }

    fun convertToLeakLevel(level: String): String {
        return when (level) {
            Level.CRITICAL.levelName -> LeakType.CRITICAL.name
            Level.HIGH.levelName -> LeakType.HIGH.name
            Level.MEDIUM.levelName -> LeakType.MEDIUM.name
            Level.LOW.levelName -> LeakType.LOW.name
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, level)
        }
    }

    /**
     * 除了[properties]中的字段，其余字段都设置为null
     */
    fun keepProps(scanPlan: ScanPlan, properties: List<KProperty<*>>) {
        ScanPlan::class.memberProperties.forEach {
            if (it is KMutableProperty<*> && it !in properties) {
                it.setter.call(scanPlan, null)
            }
        }
    }

    private fun convertToSubScanTaskStatus(status: ScanStatus): List<SubScanTaskStatus> {
        return when (status) {
            ScanStatus.INIT -> listOf(SubScanTaskStatus.CREATED, SubScanTaskStatus.PULLED, SubScanTaskStatus.ENQUEUED)
            ScanStatus.RUNNING -> listOf(SubScanTaskStatus.EXECUTING)
            ScanStatus.STOP -> listOf(SubScanTaskStatus.STOPPED)
            ScanStatus.FAILED -> listOf(SubScanTaskStatus.FAILED)
            ScanStatus.SUCCESS -> listOf(SubScanTaskStatus.SUCCESS)
        }
    }

    fun convertToScanStatus(status: String?): ScanStatus {
        return when (status) {
            SubScanTaskStatus.BLOCKED.name,
            SubScanTaskStatus.CREATED.name,
            SubScanTaskStatus.PULLED.name,
            SubScanTaskStatus.ENQUEUED.name,
            ScanTaskStatus.PENDING.name -> ScanStatus.INIT

            SubScanTaskStatus.EXECUTING.name,
            ScanTaskStatus.SCANNING_SUBMITTING.name,
            ScanTaskStatus.SCANNING_SUBMITTED.name -> ScanStatus.RUNNING

            SubScanTaskStatus.STOPPED.name,
            ScanTaskStatus.PAUSE.name,
            ScanTaskStatus.STOPPING.name,
            ScanTaskStatus.STOPPED.name -> ScanStatus.STOP

            SubScanTaskStatus.SUCCESS.name,
            ScanTaskStatus.FINISHED.name -> ScanStatus.SUCCESS

            SubScanTaskStatus.BLOCK_TIMEOUT.name,
            SubScanTaskStatus.TIMEOUT.name,
            SubScanTaskStatus.FAILED.name -> ScanStatus.FAILED
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, status.toString())
        }
    }
}
