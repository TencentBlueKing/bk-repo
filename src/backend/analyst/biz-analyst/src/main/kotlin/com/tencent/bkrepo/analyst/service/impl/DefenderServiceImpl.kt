/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.analyst.message.ScannerMessageCode
import com.tencent.bkrepo.analyst.pojo.DefenderTask
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.request.DefenderRequest
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest
import com.tencent.bkrepo.analyst.pojo.response.DefenderResponse
import com.tencent.bkrepo.analyst.service.DefenderService
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.sign.SignProperties
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfig
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.sign.SignConfigService
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.springframework.stereotype.Service

@Service
class DefenderServiceImpl(
    private val scanService: ScanService,
    private val nodeService: NodeService,
    private val signProperties: SignProperties,
    private val signConfigService: SignConfigService
) : DefenderService {
    override fun defender(request: DefenderRequest): DefenderResponse {
        with(request) {
            if (users.isEmpty()) {
                return DefenderResponse(emptyList())
            }
            Preconditions.checkArgument(batchSize > 0, DefenderRequest::batchSize.name)
            val config = signConfigService.find(projectId)
            val scanner = getScanner(config, projectId, PathUtils.resolveName(fullPath))
            val rules = ArrayList<Rule>()
            rules.add(Rule.QueryRule(NodeInfo::projectId.name, projectId, OperationType.EQ))
            rules.add(Rule.QueryRule(NodeInfo::repoName.name, repoName, OperationType.EQ))
            rules.add(Rule.QueryRule(NodeInfo::fullPath.name, fullPath, OperationType.EQ))
            val queryRule = Rule.NestedRule(rules, Rule.NestedRule.RelationType.AND)
            val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
                ?: throw NodeNotFoundException(fullPath)
            val tasks = users.chunked(batchSize).map {
                val scanRequest = ScanRequest(
                    scanner = scanner,
                    force = true,
                    rule = queryRule,
                    metadata = listOf(
                        TaskMetadata("users", it.joinToString(",")),
                        TaskMetadata("sha256", node.sha256!!),
                        TaskMetadata("host", signProperties.host),
                        TaskMetadata("projectId", projectId),
                        TaskMetadata("uploadRepoName", signProperties.signedRepoName),
                        TaskMetadata("expire", config!!.expireDays.toString()),
                        TaskMetadata(
                            "repoUrl",
                            "${signProperties.host}/generic/${node.projectId}/${signProperties.signedRepoName}"
                        )
                    )
                )
                val scanTask = scanService.scan(scanRequest, ScanTriggerType.MANUAL, SecurityUtils.getUserId())
                DefenderTask(scanTask.taskId, it)
            }
            return DefenderResponse(tasks)
        }
    }

    private fun getScanner(config: SignConfig?, projectId: String, fileName: String): String {
        val scanner = config?.scanner?.get(PathUtils.resolveExtension(fileName))
        return scanner ?: throw ErrorCodeException(ScannerMessageCode.SCANNER_NOT_FOUND, projectId)
    }
}
