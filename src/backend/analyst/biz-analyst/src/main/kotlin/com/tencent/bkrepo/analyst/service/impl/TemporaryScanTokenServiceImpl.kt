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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.configuration.ScannerProperties.Companion.EXPIRED_SECONDS
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.FileUrl
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.ToolInput
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode.RESOURCE_NOT_FOUND
import com.tencent.bkrepo.common.api.message.CommonMessageCode.SYSTEM_ERROR
import com.tencent.bkrepo.common.api.util.StreamUtils.readText
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.oci.util.OciUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStringCommands.SetOption.UPSERT
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.types.Expiration
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class TemporaryScanTokenServiceImpl(
    private val scanService: ScanService,
    private val temporaryTokenClient: ServiceTemporaryTokenClient,
    private val redisTemplate: RedisTemplate<String, String>,
    private val scannerProperties: ScannerProperties,
    private val storageService: StorageService,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val nodeClient: NodeClient
) : TemporaryScanTokenService {
    private val baseUrl
        get() = scannerProperties.baseUrl.removeSuffix(SLASH)

    override fun createToken(subtaskId: String): String {
        val token = uniqueId()
        redisTemplate.opsForValue().set(tokenKey(subtaskId), token, EXPIRED_SECONDS, TimeUnit.SECONDS)
        return token
    }

    override fun setToken(subtaskId: String, token: String) {
        redisTemplate.opsForValue().set(tokenKey(subtaskId), token, EXPIRED_SECONDS, TimeUnit.SECONDS)
    }

    override fun createExecutionClusterToken(executionClusterName: String): String {
        val token = uniqueId()
        val tokenKey = tokenKey(executionClusterName)
        val ops = redisTemplate.opsForValue()
        return if (ops.setIfAbsent(tokenKey, token) == true) {
            token
        } else {
            ops.get(tokenKey)!!
        }
    }

    override fun createToken(subtaskIds: List<String>): Map<String, String> {
        val result = HashMap<String, String>(subtaskIds.size)
        redisTemplate.executePipelined { connection ->
            subtaskIds.forEach {
                val token = uniqueId()
                result[it] = token
                val expiration = Expiration.from(EXPIRED_SECONDS, TimeUnit.SECONDS)
                connection.set(tokenKey(it).toByteArray(), token.toByteArray(), expiration, UPSERT)
            }
            null
        }
        return result
    }

    override fun checkToken(subtaskId: String, token: String?) {
        if (token == null || redisTemplate.opsForValue().get(tokenKey(subtaskId)) != token) {
            throw AuthenticationException("Invalid token, subtaskId[$subtaskId]")
        }
    }

    override fun deleteToken(subtaskId: String) {
        redisTemplate.delete(tokenKey(subtaskId))
    }

    override fun getToolInput(subtaskId: String): ToolInput {
        return getToolInput(scanService.get(subtaskId))
    }

    override fun pullToolInput(executionCluster: String): ToolInput? {
        val subtask = scanService.pull(executionCluster)
        return subtask?.let {
            logger.info("executionCluster[$executionCluster] pull subtask[${it.taskId}]")
            getToolInput(it)
        }
    }

    private fun getToolInput(subtask: SubScanTask): ToolInput {
        // 设置后续操作使用的用户的身份为任务触发者
        HttpContextHolder.getRequestOrNull()?.setAttribute(USER_KEY, subtask.createdBy)

        val scanner = subtask.scanner as StandardScanner
        with(subtask) {
            val fullPaths = getFullPaths(subtask)
            // 创建临时访问文件的token
            val req = TemporaryTokenCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPathSet = fullPaths.keys,
                expireSeconds = Duration.ofMinutes(30L).seconds,
                permits = 1,
                type = TokenType.DOWNLOAD
            )
            val tokens = temporaryTokenClient.createToken(req)
            if (tokens.isNotOk()) {
                throw SystemErrorException(SYSTEM_ERROR, "create token failed, subtask[$subtask], res[$tokens]")
            }

            val tokenMap = tokens.data!!.associateBy { it.fullPath }
            val fileUrls = fullPaths.map { (key, value) ->
                val url = tokenMap[key]!!.let {
                    "$baseUrl/api/generic/temporary/download" +
                        "/${it.projectId}/${it.repoName}${it.fullPath}?token=${it.token}"
                }
                value.copy(url = url)
            }

            val args = ToolInput.generateArgs(scanner, repoType, packageSize, packageKey, version)
            return ToolInput.create(taskId, fileUrls, args)
        }
    }

    private fun getFullPaths(subtask: SubScanTask): Map<String, FileUrl> = with(subtask) {
        return if (repoType == RepositoryType.DOCKER.name) {
            val storageCredentials = credentialsKey?.let { storageCredentialsClient.findByKey(it).data!! }
            val manifestContent = storageService.load(sha256, Range.full(size), storageCredentials)?.readText()
                ?: throw ErrorCodeException(RESOURCE_NOT_FOUND, "file [$projectId:$repoName:$fullPath] not found")
            val schemeVersion = OciUtils.schemeVersion(manifestContent)
            val fullPaths = LinkedHashMap<String, FileUrl>()
            // 将manifest下载链接加入fullPaths列表，需要保证map第一项是manifest文件
            fullPaths[fullPath] = convert(subtask)
            // 获取layer对应的nodes
            val nodes = if (schemeVersion.schemaVersion == 1) {
                val manifest = OciUtils.stringToManifestV1(manifestContent)
                getNodes(projectId, repoName, manifest.fsLayers.map { it.sha256 })
            } else {
                val manifest = OciUtils.stringToManifestV2(manifestContent)
                val sha256List = mutableListOf(manifest.config.sha256)
                sha256List.addAll(manifest.layers.map { it.sha256 })
                // 添加config与layer下载链接
                getNodes(projectId, repoName, sha256List)
            }
            // 转化layer node为fileUrl
            nodes.forEach {
                val fullPath = it[NodeDetail::fullPath.name]!!.toString()
                val sha256 = it[NodeDetail::sha256.name]!!.toString()
                val size = it[NodeDetail::size.name]!!.toString().toLong()
                fullPaths[fullPath] = FileUrl("", fullPath.substringAfterLast(SLASH), sha256, size)
            }
            fullPaths
        } else {
            mapOf(subtask.fullPath to convert(subtask))
        }
    }

    private fun tokenKey(subtaskId: String) = "scanner:token:$subtaskId"
    private fun convert(subtask: SubScanTask, url: String = "") =
        FileUrl(url, subtask.fullPath.substringAfterLast(SLASH), subtask.sha256, subtask.size)

    private fun getNodes(projectId: String, repoName: String, sha256: List<String>): List<Map<String, Any?>> {
        val distinctNodes = HashMap<String, Map<String, Any?>>()

        var pageNumber = DEFAULT_PAGE_NUMBER
        val pageSize = DEFAULT_PAGE_SIZE
        while (true) {
            val res = nodeClient.search(
                NodeQueryBuilder()
                    .projectId(projectId)
                    .repoName(repoName)
                    .rule(NodeDetail::sha256.name, sha256, OperationType.IN)
                    .select(NodeDetail::fullPath.name, NodeDetail::size.name, NodeDetail::sha256.name)
                    .page(pageNumber++, pageSize)
                    .build()
            )

            if (res.isNotOk()) {
                logger.error("get image nodes failed, msg[${res.message}], sha256[$sha256]")
                throw ErrorCodeException(SYSTEM_ERROR)
            }

            // 同一个sha256可能会查询到多个node，需要去重
            res.data!!.records.forEach { distinctNodes[it[NodeDetail::sha256.name]!!.toString()] = it }
            if (res.data!!.records.size < pageSize || res.data!!.totalRecords.toInt() == res.data!!.records.size) {
                break
            }
        }
        return distinctNodes.values.toList()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TemporaryScanTokenServiceImpl::class.java)
    }
}
