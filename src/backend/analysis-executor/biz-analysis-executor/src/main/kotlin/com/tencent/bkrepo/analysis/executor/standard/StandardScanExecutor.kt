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

package com.tencent.bkrepo.analysis.executor.standard

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.analysis.executor.CommonScanExecutor
import com.tencent.bkrepo.analysis.executor.configuration.DockerProperties
import com.tencent.bkrepo.analysis.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.analysis.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.analysis.executor.util.CommonUtils.readJsonString
import com.tencent.bkrepo.analysis.executor.util.DockerScanHelper
import com.tencent.bkrepo.analysis.executor.util.FileUtils
import com.tencent.bkrepo.analysis.executor.util.ImageScanHelper
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner.ArgumentType.STRING
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.ToolInput
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.ToolOutput
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File

@ConditionalOnProperty(DockerProperties.SCANNER_EXECUTOR_DOCKER_ENABLED, matchIfMissing = true)
@Component(StandardScanner.TYPE)
class StandardScanExecutor(
    dockerClient: DockerClient,
    nodeClient: NodeClient,
    storageService: StorageService,
    private val scannerExecutorProperties: ScannerExecutorProperties
) : CommonScanExecutor() {

    private val dockerScanHelper = DockerScanHelper(scannerExecutorProperties, dockerClient)
    private val imageScanHelper = ImageScanHelper(nodeClient, storageService)

    override fun doScan(
        taskWorkDir: File,
        scannerInputFile: File,
        sha256: String,
        task: ScanExecutorTask
    ): SubScanTaskStatus {
        val scanner = task.scanner as StandardScanner
        val inputFile = generateInputFile(task, taskWorkDir, scannerInputFile, sha256)
        val args = ArrayList<String>()
        args.addAll(scanner.cmd.split(" "))
        args.add("--input")
        args.add(convertToContainerPath(inputFile.absolutePath, taskWorkDir))
        args.add("--output")
        args.add("$CONTAINER_WORK_DIR/$OUTPUT_FILE")
        val result = dockerScanHelper.scan(
            image = scanner.image,
            binds = Binds(Bind(taskWorkDir.absolutePath, Volume(CONTAINER_WORK_DIR))),
            args = args,
            scannerInputFile = scannerInputFile,
            task = task
        )
        return if (result) {
            SubScanTaskStatus.SUCCESS
        } else {
            SubScanTaskStatus.TIMEOUT
        }
    }

    override fun loadFileTo(scannerInputFile: File, task: ScanExecutorTask): String {
        return if (task.repoType == RepositoryType.DOCKER.name) {
            scannerInputFile.parentFile.mkdirs()
            scannerInputFile.outputStream().use { imageScanHelper.generateScanFile(it, task) }
        } else {
            super.loadFileTo(scannerInputFile, task)
        }
    }

    override fun workDir() = File(scannerExecutorProperties.workDir)

    override fun scannerInputFile(taskWorkDir: File, task: ScanExecutorTask): File {
        val fileName = if (task.repoType == RepositoryType.DOCKER.name) {
            "${task.sha256}.tar"
        } else {
            FileUtils.sha256NameWithExt(task.fullPath, task.sha256)
        }
        return File(taskWorkDir, fileName)
    }

    override fun result(taskWorkDir: File, task: ScanExecutorTask, scanStatus: SubScanTaskStatus): ScanExecutorResult {
        val toolOutput = readJsonString<ToolOutput>(File(taskWorkDir, OUTPUT_FILE))
        toolOutput?.err?.let { logger.warn("task[${task.taskId}] scan failed, message:\n$it") }
        return if (toolOutput != null) {
            StandardScanExecutorResult(toolOutput)
        } else {
            StandardScanExecutorResult()
        }
    }

    override fun stop(taskId: String): Boolean {
        return dockerScanHelper.stop(taskId)
    }

    private fun generateInputFile(
        task: ScanExecutorTask,
        workDir: File,
        scannerInputFile: File,
        sha256: String
    ): File {
        val scanner = task.scanner as StandardScanner
        val inputFile = File(workDir, INPUT_FILE)
        val args = scanner.args.toMutableList()
        args.add(StandardScanner.Argument(STRING.name, StandardScanner.ARG_KEY_PKG_TYPE, task.repoType))
        val toolInput = ToolInput.create(
            task.taskId, scanner, task.repoType, scannerInputFile.length(),
            convertToContainerPath(scannerInputFile.absolutePath, workDir), sha256
        )
        inputFile.writeText(toolInput.toJsonString())
        return inputFile
    }

    private fun convertToContainerPath(path: String, taskWorkDir: File): String {
        val subPath = path.substringAfter(
            "${taskWorkDir.absolutePath.trimEnd(File.separatorChar)}${File.separatorChar}"
        )
        return "$CONTAINER_WORK_DIR/$subPath"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StandardScanner::class.java)
        private const val INPUT_FILE = "input.json"
        private const val OUTPUT_FILE = "output.json"
        private const val CONTAINER_WORK_DIR = "/bkrepo/workspace"
    }

}
