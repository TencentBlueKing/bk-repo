/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.pojo.webhook.BkCiDevXEnabledPayload
import com.tencent.bkrepo.repository.service.webhook.BkciWebhookListener
import io.swagger.annotations.Api
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("用于监听蓝盾事件")
@RestController
@RequestMapping("/api/webhook/receiver/bkci")
class UserBkCiWebhookController(
    private val repositoryProperties: RepositoryProperties,
    private val executor: ThreadPoolTaskExecutor,
    private val listeners: ObjectProvider<BkciWebhookListener>
) {
    @PostMapping(headers = ["X-DEVOPS-EVENT=DEVX_ENABLED"])
    fun onDevXEnabled(
        @RequestHeader("X-DEVOPS-EVENT") event: String,
        @RequestHeader("X-DEVOPS-SIGNATURE-256") signature: String,
        @RequestBody payload: String
    ) {
        logger.info("receive webhook, event[$event], signature[$signature], payload[$payload]")
        verifySignature(signature, payload)

        val devXEnabledPayload = payload.readJsonString<BkCiDevXEnabledPayload>()
        executor.execute {
            listeners.forEach { it.onDevXEnabled(devXEnabledPayload) }
        }
    }

    private fun verifySignature(signature: String, payload: String) {
        val key = repositoryProperties.bkciWebhookSecret
        if (key.isEmpty()) {
            logger.error("bkci webhook secret was not configured")
            throw SystemErrorException()
        }

        val expectedSignature = HmacUtils(HmacAlgorithms.HMAC_SHA_256, key).hmacHex(payload)
        if (!signature.equals(expectedSignature, true)) {
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "X-DEVOPS-SIGNATURE-256")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserBkCiWebhookController::class.java)
    }
}
