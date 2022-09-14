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

package com.tencent.bkrepo.analysis.image

import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.analysis.executor.ScanExecutor
import com.tencent.bkrepo.analysis.executor.util.Converter.convert
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

object ScanRunner {
    private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 5L
    private const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
    private const val DEFAULT_WRITE_TIMEOUT_SECONDS = 30L
    private const val API_REPORT = "api/scanner/temporary/report"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    private val logger = LoggerFactory.getLogger(ScanRunner::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val task = args[0].readJsonString<SubScanTask>()
        val serverHost = args[1]
        val workDirPath = args[2]
        val workDir = File(workDirPath)
        val token = task.token ?: throw IllegalArgumentException("token is null")

        try {
            val url = task.url ?: throw IllegalArgumentException("file url is null")
            val executorTask = convert(task, loadFile(url))
            val result = createExecutor(workDir, task.scanner.type).scan(executorTask)
            report(serverHost, token, task.taskId, task.parentScanTaskId, result)
        } catch (e: Exception) {
            logger.error("scan failed[$task]", e)
            report(serverHost, token, task.taskId, task.parentScanTaskId)
        }
    }

    private fun loadFile(url: String): InputStream {
        val req = Request.Builder().get().url(url).build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) {
            throw RuntimeException("load file[$url] failed")
        }
        return res.body()!!.byteStream()
    }

    private fun createExecutor(workDir: File, type: String): ScanExecutor {
        return when (type) {
            ArrowheadScanner.TYPE -> ArrowheadCmdScanExecutor(workDir)
            else -> throw IllegalArgumentException("unknown scanner type[$type]")
        }
    }

    private fun report(
        host: String,
        token: String,
        subtaskId: String,
        parentTaskId: String,
        result: ScanExecutorResult? = null
    ) {
        val content = ReportResultRequest(
            subTaskId = subtaskId,
            scanStatus = result?.scanStatus ?: SubScanTaskStatus.FAILED.name,
            scanExecutorResult = result,
            token = token
        )
        val body = RequestBody.create(MediaType.parse(MediaTypes.APPLICATION_JSON), content.toJsonString())
        val req = Request.Builder().post(body).url("$host/$API_REPORT").build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) {
            logger.error(
                "report result failed, " +
                    "taskId[$subtaskId], parentTaskId[$parentTaskId], token[$token], " +
                    "res[${res}]"
            )
        }
        val resBody = res.body()?.string()?.readJsonString<Response<Void>>()
        if (resBody?.code != 0) {
            logger.error("report result failed, response body[$resBody]")
        }
    }
}
