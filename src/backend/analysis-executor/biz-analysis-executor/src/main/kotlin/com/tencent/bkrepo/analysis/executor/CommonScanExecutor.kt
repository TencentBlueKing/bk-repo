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

package com.tencent.bkrepo.analysis.executor

import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.analysis.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.analysis.executor.util.CommonUtils
import com.tencent.bkrepo.analysis.executor.util.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

abstract class CommonScanExecutor : ScanExecutor {
    override fun scan(task: ScanExecutorTask): ScanExecutorResult {
        val taskWorkDir = createTaskWorkDir(workDir(), task.scanner.rootPath, task.taskId)
        try {
            val scannerInputFile = scannerInputFile(taskWorkDir, task)
            val sha256 = loadFileTo(scannerInputFile, task)
            val status = doScan(taskWorkDir, scannerInputFile, sha256, task)
            return result(taskWorkDir, task, status)
        } finally {
            // 清理工作目录
            if (task.scanner.cleanWorkDir) {
                FileUtils.deleteRecursively(taskWorkDir)
            }
        }
    }

    /**
     * 执行扫描
     *
     * @param taskWorkDir 任务工作目录
     * @param scannerInputFile 待扫描文件
     * @param sha256 [scannerInputFile]的sha256
     * @param task 扫描任务
     *
     * @return 扫描任务状态
     */
    protected abstract fun doScan(
        taskWorkDir: File,
        scannerInputFile: File,
        sha256: String,
        task: ScanExecutorTask
    ): SubScanTaskStatus

    /**
     * 获取工作目录
     */
    protected abstract fun workDir(): File

    /**
     * 创建扫描任务工作目录，默认创建的目录为[workDir]/[scannerDir]/[taskId]，如果目录存在会先删除旧目录
     */
    private fun createTaskWorkDir(workDir: File, scannerDir: String, taskId: String): File {
        // 创建工作目录
        val taskWorkDir = File(File(workDir, scannerDir), taskId)
        if (!taskWorkDir.deleteRecursively() || !taskWorkDir.mkdirs()) {
            throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR, taskWorkDir.absolutePath)
        }
        logger.info("create task work dir success[$taskWorkDir]")
        return taskWorkDir
    }

    /**
     * 将待扫描文件写入[scannerInputFile]
     */
    private fun loadFileTo(scannerInputFile: File, task: ScanExecutorTask): String {
        // 加载待扫描文件，Arrowhead依赖文件名后缀判断文件类型进行解析，所以需要加上文件名后缀
        scannerInputFile.parentFile.mkdirs()
        Files.move(task.file.toPath(), scannerInputFile.toPath())
        logger.info(CommonUtils.buildLogMsg(task, "move file to task work directory success"))
        return task.sha256
    }

    /**
     * 默认会加载待扫描文件到该方法返回的文件中
     */
    protected abstract fun scannerInputFile(taskWorkDir: File, task: ScanExecutorTask): File

    /**
     * 获取扫描结果
     *
     * @param taskWorkDir 扫描任务工作目录
     * @param task 扫描任务
     * @param scanStatus 扫描任务状态
     *
     * @return 扫描结果
     */
    protected abstract fun result(
        taskWorkDir: File,
        task: ScanExecutorTask,
        scanStatus: SubScanTaskStatus
    ): ScanExecutorResult

    companion object {
        private val logger = LoggerFactory.getLogger(CommonScanExecutor::class.java)
    }
}
