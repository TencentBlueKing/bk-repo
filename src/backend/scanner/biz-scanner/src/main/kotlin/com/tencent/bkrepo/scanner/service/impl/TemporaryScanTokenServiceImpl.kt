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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.scanner.pojo.scanner.standard.FileUrl
import com.tencent.bkrepo.common.scanner.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.standard.StandardScanner.Companion.ARG_KEY_PKG_TYPE
import com.tencent.bkrepo.common.scanner.pojo.scanner.standard.ToolInput
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.repository.api.TemporaryTokenClient
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.scanner.configuration.ScannerProperties
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.service.ScanService
import com.tencent.bkrepo.scanner.service.TemporaryScanTokenService
import org.springframework.data.redis.connection.RedisStringCommands.SetOption.UPSERT
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.types.Expiration
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class TemporaryScanTokenServiceImpl(
    private val scanService: ScanService,
    private val temporaryTokenClient: TemporaryTokenClient,
    private val redisTemplate: RedisTemplate<String, String>,
    private val scannerProperties: ScannerProperties
) : TemporaryScanTokenService {
    override fun createToken(subtaskId: String): String {
        val token = generateToken()
        redisTemplate.opsForValue().set(tokenKey(subtaskId), token, EXPIRED_SECONDS, TimeUnit.SECONDS)
        return token
    }

    override fun createToken(subtaskIds: List<String>): Map<String, String> {
        val result = HashMap<String, String>(subtaskIds.size)
        redisTemplate.executePipelined { connection ->
            subtaskIds.forEach {
                val token = generateToken()
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
        val subtask = scanService.get(subtaskId)
        val scanner = subtask.scanner as StandardScanner
        with(subtask) {
            val fullPaths = getFullPaths(subtask)
            // 创建临时访问文件的token
            val req = TemporaryTokenCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPathSet = fullPaths.keys,
                expireSeconds = java.time.Duration.ofMinutes(30L).seconds,
                permits = 1,
                type = com.tencent.bkrepo.repository.pojo.token.TokenType.DOWNLOAD
            )
            val tokens = temporaryTokenClient.createToken(req)
            if (tokens.isNotOk()) {
                throw SystemErrorException(
                    CommonMessageCode.SYSTEM_ERROR, "create token failed, subtask[$subtask], res[$tokens]"
                )
            }

            val args = scanner.args.toMutableList()
            args.add(StandardScanner.Argument(StandardScanner.ArgumentType.STRING.name, ARG_KEY_PKG_TYPE, repoType))
            return ToolInput.create(
                taskId, scanner, repoType, subtask.size, generateDownloadUrl(fullPaths, tokens.data!!)
            )
        }
    }

    private fun getFullPaths(subtask: SubScanTask): Map<String, String> {
        return if (subtask.repoType == RepositoryType.DOCKER.name) {
            TODO("")
        } else {
            mapOf(subtask.fullPath to subtask.sha256)
        }
    }

    private fun tokenKey(subtaskId: String) = "scanner:token:$subtaskId"

    private fun generateDownloadUrl(fullPaths: Map<String, String>, tokens: List<TemporaryTokenInfo>): List<FileUrl> {
        val baseUrl = scannerProperties.baseUrl.removeSuffix(SLASH)
        return tokens.map {
            FileUrl(
                "$baseUrl/temporary/download/${it.projectId}/${it.repoName}${it.fullPath}?token=${it.token}",
                it.fullPath.substringAfterLast(SLASH),
                fullPaths[it.fullPath]!!
            )
        }
    }

    private fun generateToken(): String {
        return UUID.randomUUID().toString().replace(StringPool.DASH, StringPool.EMPTY).toLowerCase()
    }

    companion object {
        private const val EXPIRED_SECONDS = 24 * 60 * 60L
    }
}
