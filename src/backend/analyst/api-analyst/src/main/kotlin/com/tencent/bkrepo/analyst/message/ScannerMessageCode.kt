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

package com.tencent.bkrepo.analyst.message

import com.tencent.bkrepo.common.api.message.MessageCode

enum class ScannerMessageCode(
    private val key: String,
    private val businessCode: Int
) : MessageCode {
    SCANNER_NOT_FOUND("scanner.scanner.not-found", 0),
    SCANNER_EXISTS("scanner.scanner.exists", 1),
    SCAN_TASK_NOT_FOUND("scanner.task.not-found", 2),
    SCANNER_RESULT_TYPE_INVALID("scanner.result.type.invalid", 3),
    SCAN_PLAN_DELETE_FAILED("scanner.plan.delete-failed", 4),
    SCAN_TASK_COUNT_EXCEED_LIMIT("scanner.task.count.exceed-limit", 5),
    SCAN_TASK_NAME_BATCH_SCAN("scanner.task.name.manual", 6),
    SCAN_TASK_NAME_SINGLE_SCAN("scanner.task.name.manual.single", 7),
    SCAN_REPORT_NOTIFY_MESSAGE_SCANNED("scanner.report.notify.message.scanned", 8),
    SCAN_REPORT_NOTIFY_MESSAGE_CVE("scanner.report.notify.message.cve", 10),
    SCAN_REPORT_NOTIFY_MESSAGE_DETAIL("scanner.report.notify.message.detail", 14),
    SCAN_REPORT_NOTIFY_MESSAGE_TITLE("scanner.report.notify.message.title", 15),
    SCAN_REPORT_NOTIFY_MESSAGE_TRIGGER_TIME("scanner.report.notify.message.trigger.time", 16),
    SCAN_REPORT_NOTIFY_MESSAGE_TRIGGER_USER("scanner.report.notify.message.trigger.user", 17),
    LICENSE_NOT_FOUND("license.not-found", 18),
    SCAN_REPORT_NOTIFY_MESSAGE_LICENSE("scanner.report.notify.message.license", 19),
    SCAN_REPORT_NOTIFY_MESSAGE_SENSITIVE("scanner.report.notify.message.sensitive", 20),
    EXPORT_REPORT_FAIL("export.report.fail", 21),
    EXPORT_REPORT_STATUS_INIT("export.report.status.init", 22),
    EXPORT_REPORT_STATUS_RUNNING("export.report.status.running", 23),
    EXPORT_REPORT_STATUS_STOP("export.report.status.stop", 24),
    EXPORT_REPORT_STATUS_SUCCESS("export.report.status.success", 25),
    EXPORT_REPORT_STATUS_UN_QUALITY("export.report.status.un.quality", 26),
    EXPORT_REPORT_STATUS_QUALITY_PASS("export.report.status.quality.pass", 27),
    EXPORT_REPORT_STATUS_QUALITY_UN_PASS("export.report.status.quality.un.pass", 28),
    EXPORT_REPORT_STATUS_FAILED("export.report.status.failed", 29),
    ANALYST_ARTIFACT_DELETED("analyst.artifact.deleted", 30),
    ANALYST_TASK_EXCEED_MAX_GLOBAL_TASK_COUNT("analyst.task.global.count.exceed", 31);

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 16
}
