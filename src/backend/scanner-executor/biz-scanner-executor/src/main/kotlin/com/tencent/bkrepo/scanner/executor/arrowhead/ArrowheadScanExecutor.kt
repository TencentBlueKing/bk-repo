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

package com.tencent.bkrepo.scanner.executor.arrowhead

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Volume
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseNature
import com.tencent.bkrepo.common.scanner.pojo.scanner.LicenseOverviewKey
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ApplicationItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.CveSecItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.arrowhead.SensitiveItem
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.scanner.api.ScanClient
import com.tencent.bkrepo.scanner.executor.configuration.DockerProperties.Companion.SCANNER_EXECUTOR_DOCKER_ENABLED
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.scanner.executor.util.CommonUtils.incLicenseOverview
import com.tencent.bkrepo.scanner.executor.util.DockerScanHelper
import com.tencent.bkrepo.scanner.executor.util.ImageScanHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.io.File

@Component(ArrowheadScanner.TYPE)
@ConditionalOnProperty(SCANNER_EXECUTOR_DOCKER_ENABLED, matchIfMissing = true)
class ArrowheadScanExecutor @Autowired constructor(
    dockerClient: DockerClient,
    nodeClient: NodeClient,
    storageService: StorageService,
    private val scanClient: ScanClient,
    private val scannerExecutorProperties: ScannerExecutorProperties
) : AbsArrowheadScanExecutor() {

    @Value(CONFIG_FILE_TEMPLATE_CLASS_PATH)
    private lateinit var arrowheadConfigTemplate: Resource
    private val configTemplate by lazy { arrowheadConfigTemplate.inputStream.use { it.reader().readText() } }

    private val workDir by lazy { File(scannerExecutorProperties.workDir) }
    private val dockerScanHelper = DockerScanHelper(scannerExecutorProperties, dockerClient)
    private val imageScanHelper = ImageScanHelper(nodeClient, storageService)

    override fun doScan(
        taskWorkDir: File,
        scannerInputFile: File,
        configFile: File,
        task: ScanExecutorTask
    ): SubScanTaskStatus {
        require(task.scanner is ArrowheadScanner)
        val containerConfig = task.scanner.container

        // 容器内tmp目录
        val tmpDir = createTmpDir(taskWorkDir)
        val tmpBind = Bind(tmpDir.absolutePath, Volume("/tmp"))
        // 容器内工作目录
        val bind = Bind(taskWorkDir.absolutePath, Volume(containerConfig.workDir))

        // 执行扫描
        val result = dockerScanHelper.scan(
            containerConfig.image, Binds(tmpBind, bind), listOf(containerConfig.args),
            taskWorkDir, scannerInputFile, task
        )
        if (!result) {
            return scanStatus(task, taskWorkDir, SubScanTaskStatus.TIMEOUT)
        }
        return scanStatus(task, taskWorkDir)
    }

    override fun loadFileTo(scannerInputFile: File, task: ScanExecutorTask) {
        if (task.repoType == RepositoryType.DOCKER.name) {
            scannerInputFile.parentFile.mkdirs()
            scannerInputFile.outputStream().use { imageScanHelper.generateScanFile(it, task) }
        } else {
            super.loadFileTo(scannerInputFile, task)
        }
    }

    override fun stop(taskId: String): Boolean {
        return dockerScanHelper.stop(taskId)
    }

    override fun workDir() = workDir

    override fun configTemplate() = configTemplate

    override fun additionalOverview(
        overview: MutableMap<String, Long>,
        applicationItems: List<ApplicationItem>,
        sensitiveItems: List<SensitiveItem>,
        cveSecItems: List<CveSecItem>
    ) {
        val licenseIds = HashSet<String>()
        val licenses = HashSet<ApplicationItem>()
        applicationItems.forEach { item ->
            item.license?.let {
                licenses.add(item)
                licenseIds.add(it.name)
            }
        }
        overview[LicenseOverviewKey.overviewKeyOf(LicenseOverviewKey.TOTAL)] = licenses.size.toLong()

        // 获取许可证详情
        val licenseInfo = scanClient.licenseInfoByIds(licenseIds.toList()).data!!.mapKeys { it.key.toLowerCase() }
        for (license in licenses) {
            val detail = licenseInfo[license.license!!.name.toLowerCase()]
            if (detail == null) {
                incLicenseOverview(overview, LicenseNature.UNKNOWN.natureName)
                continue
            }

            if (detail.isDeprecatedLicenseId) {
                incLicenseOverview(overview, LicenseNature.UN_COMPLIANCE.natureName)
            }

            if (!detail.isTrust) {
                incLicenseOverview(overview, LicenseNature.UN_RECOMMEND.natureName)
            }
        }
    }

    private fun createTmpDir(workDir: File): File {
        val tmpDir = File(workDir, TMP_DIR_NAME)
        tmpDir.mkdirs()
        return tmpDir
    }

    companion object {

        /**
         * 扫描器配置文件路径
         */
        private const val CONFIG_FILE_TEMPLATE_CLASS_PATH = "classpath:standalone.toml"

        const val TMP_DIR_NAME = "tmp"
    }
}
