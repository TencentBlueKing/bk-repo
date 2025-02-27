/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.utils

import com.tencent.bkrepo.analyst.PROJECT_ID
import com.tencent.bkrepo.analyst.REPO
import com.tencent.bkrepo.analyst.UT_CREDENTIALS_KEY
import com.tencent.bkrepo.analyst.UT_PLAN_ID
import com.tencent.bkrepo.analyst.UT_SCANNER
import com.tencent.bkrepo.analyst.UT_USER
import com.tencent.bkrepo.analyst.component.manager.standard.model.TSecurityResult
import com.tencent.bkrepo.analyst.component.manager.standard.model.TSecurityResultData
import com.tencent.bkrepo.analyst.model.TArchiveSubScanTask
import com.tencent.bkrepo.analyst.model.TFileScanResult
import com.tencent.bkrepo.analyst.model.TScanPlan
import com.tencent.bkrepo.analyst.model.TScanResult
import com.tencent.bkrepo.analyst.model.TScanTask
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.common.analysis.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.LicenseResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.Result
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.SecurityResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.SensitiveResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.ToolOutput
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.VersionPaths
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.storage.core.locator.HashFileLocator
import java.time.LocalDateTime

val fileLocator = HashFileLocator()

fun buildScanPlan(now: LocalDateTime = LocalDateTime.now(), overview: Map<String, Long>) = TScanPlan(
    id = UT_PLAN_ID,
    createdBy = UT_USER,
    createdDate = now,
    lastModifiedBy = UT_USER,
    lastModifiedDate = now,
    name = "plan",
    type = RepositoryType.GENERIC.name,
    scanner = UT_SCANNER,
    description = "",
    rule = "",
    projectId = PROJECT_ID,
    scanResultOverview = overview
)

fun buildScanTask(now: LocalDateTime = LocalDateTime.now()) = TScanTask(
    createdBy = UT_USER,
    createdDate = now,
    lastModifiedBy = UT_USER,
    lastModifiedDate = now,
    name = "Security Scan Task",
    startDateTime = now,
    finishedDateTime = now,
    triggerType = ScanTriggerType.MANUAL.name,
    planId = UT_PLAN_ID,
    projectId = PROJECT_ID,
    projectIds = emptySet(),
    status = ScanTaskStatus.FINISHED.name,
    rule = "",
    total = 10,
    scanning = 0,
    failed = 5,
    scanned = 5,
    passed = 5,
    scanner = UT_SCANNER,
    scannerType = StandardScanner.TYPE,
    scannerVersion = "1.0.0",
    scanResultOverview = emptyMap(),
    metadata = emptyList()
)

fun buildPlanSubScanTask(subtask: TArchiveSubScanTask) = SubtaskConverter.convertToPlanSubtask(
    subtask, subtask.status
)

fun buildArchiveSubScanTask(
    taskId: String,
    sha256: String,
    now: LocalDateTime = LocalDateTime.now()
) = TArchiveSubScanTask(
    createdBy = UT_USER,
    createdDate = now,
    lastModifiedBy = UT_USER,
    lastModifiedDate = now,
    startDateTime = now,
    finishedDateTime = now,
    triggerType = ScanTriggerType.MANUAL.name,
    parentScanTaskId = taskId,
    planId = UT_PLAN_ID,
    projectId = PROJECT_ID,
    repoName = REPO,
    repoType = RepositoryType.GENERIC.name,
    fullPath = fileLocator.locate(sha256),
    artifactName = "demo-1.0.0.jar",
    status = SubScanTaskStatus.SUCCESS.name,
    executedTimes = 1,
    scanner = UT_SCANNER,
    scannerType = StandardScanner.TYPE,
    sha256 = sha256,
    size = 1024L,
    packageSize = 1024L,
    credentialsKey = UT_CREDENTIALS_KEY,
    scanResultOverview = mapOf(
        CveOverviewKey.CVE_LOW_COUNT.key to 1L,
        CveOverviewKey.CVE_HIGH_COUNT.key to 2,
    ),
)

fun buildSubScanTask(taskId: String, sha256: String, now: LocalDateTime = LocalDateTime.now()) = TSubScanTask(
    id = "subTaskId",
    createdBy = UT_USER,
    createdDate = now,
    lastModifiedBy = UT_USER,
    lastModifiedDate = now,
    startDateTime = now,
    triggerType = ScanTriggerType.MANUAL.name,
    parentScanTaskId = taskId,
    planId = UT_PLAN_ID,
    projectId = PROJECT_ID,
    repoName = REPO,
    repoType = RepositoryType.GENERIC.name,
    fullPath = fileLocator.locate(sha256),
    artifactName = "demo-1.0.0.jar",
    status = SubScanTaskStatus.SUCCESS.name,
    executedTimes = 1,
    scanner = UT_SCANNER,
    scannerType = StandardScanner.TYPE,
    sha256 = sha256,
    size = 1024L,
    packageSize = 1024L,
    credentialsKey = UT_CREDENTIALS_KEY,
)

fun buildScanExecutorResult(): StandardScanExecutorResult {
    val securityResults = listOf(
        SecurityResult(
            vulId = "CVE-123",
            versionsPaths = mutableSetOf(
                VersionPaths("1.1", mutableSetOf("/a/b/c.x", "/b/c/d.x")),
                VersionPaths("1.2", mutableSetOf("/a/b/c.x", "/b/c/d.x"))
            ),
            cvss = 1.1,
            pkgName = "test-pkg",
            pkgVersions = mutableSetOf("1.1", "1.2"),
            severity = Level.CRITICAL.name,
        ),
        SecurityResult(
            vulId = "CVE-123",
            versionsPaths = mutableSetOf(
                VersionPaths("1.1", mutableSetOf("/a/b/c.x", "/b/c/d.x")),
                VersionPaths("1.2", mutableSetOf("/a/b/c.x", "/b/c/d.x"))
            ),
            cvss = 1.1,
            pkgName = "test-pkg2",
            pkgVersions = mutableSetOf("1.1", "1.2"),
            severity = Level.CRITICAL.name,
        ),
    )

    val licenseResults = listOf(
        LicenseResult(
            pkgName = "test-pkg",
            pkgVersions = mutableSetOf("1.1", "1.2"),
            licenseName = "MIT",
            versionsPaths = mutableSetOf(
                VersionPaths("1.1", mutableSetOf("/a/b/c.x", "/b/c/d.x")),
                VersionPaths("1.2", mutableSetOf("/a/b/c.x", "/b/c/d.x"))
            ),
        )
    )

    val sensitiveResults = listOf(
        SensitiveResult("/a/b/c", "AWS", "xxx****xxxx"),
        SensitiveResult("/a/b/e", "EMAIL", "xxx****xxxx")
    )

    return StandardScanExecutorResult(
        ToolOutput(
            status = SubScanTaskStatus.SUCCESS.name,
            result = Result(
                securityResults = securityResults,
                licenseResults = licenseResults,
                sensitiveResults = sensitiveResults,
            )
        )
    )
}

fun buildFileResult(now: LocalDateTime, sha256: String, taskId: String) = TFileScanResult(
    id = null,
    lastModifiedDate = now,
    sha256 = sha256,
    credentialsKey = UT_CREDENTIALS_KEY,
    mapOf(UT_SCANNER to buildScanResult(now, taskId))
)

fun buildScanResult(now: LocalDateTime, taskId: String) = TScanResult(
    taskId = taskId,
    startDateTime = now,
    finishedDateTime = now,
    scanner = UT_SCANNER,
    scannerType = StandardScanner.TYPE,
    scannerVersion = "1.0.0",
    overview = emptyMap()
)

fun buildSecurityResult(sha256: String, scanner: String = UT_SCANNER) = TSecurityResult(
    id = null,
    credentialsKey = UT_CREDENTIALS_KEY,
    sha256 = sha256,
    scanner = scanner,
    data = TSecurityResultData(vulId = "", severity = "CRITICAL")
)

fun randomSha256(): String {
    val charPool = ('a'..'f') + ('0'..'9')
    return (1..64)
        .map { charPool.random() }
        .joinToString("")
}
