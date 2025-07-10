/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.notify.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.NotifyChannelCredential
import com.tencent.bkrepo.common.notify.model.TNotifyChannelCredential
import com.tencent.bkrepo.common.notify.repository.NotifyChannelCredentialRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class NotifyChannelCredentialService(
    private val notifyChannelCredentialRepository: NotifyChannelCredentialRepository
) {
    @Transactional(rollbackFor = [Throwable::class])
    fun create(userId: String, notifyChannelCredential: NotifyChannelCredential): NotifyChannelCredential {
        with(notifyChannelCredential) {
            if (notifyChannelCredentialRepository.existsByName(name)) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, name)
            }

            val now = LocalDateTime.now()
            val credential = TNotifyChannelCredential(
                createdBy = userId,
                createdDate = now,
                lastModifiedBy = userId,
                lastModifiedDate = now,
                name = name,
                type = type,
                default = default,
                credential = notifyChannelCredential.toJsonString()
            )
            notifyChannelCredentialRepository.insert(credential)
            return notifyChannelCredential
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun delete(name: String) {
        if (!notifyChannelCredentialRepository.existsByName(name)) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, name)
        }
        notifyChannelCredentialRepository.deleteByName(name)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun update(userId: String, notifyChannelCredential: NotifyChannelCredential): NotifyChannelCredential {
        with(notifyChannelCredential) {
            val old = notifyChannelCredentialRepository.findByName(name)
                ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND)

            // 通知渠道名和类型不能更改
            if (notifyChannelCredential.name != old.name || notifyChannelCredential.type != old.type) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
            }

            old.default = default
            old.credential = notifyChannelCredential.toJsonString()
            old.lastModifiedBy = userId
            old.lastModifiedDate = LocalDateTime.now()
            notifyChannelCredentialRepository.save(old)
            return notifyChannelCredential
        }
    }

    @Transactional(rollbackFor = [Throwable::class], readOnly = true)
    fun list(): List<NotifyChannelCredential> {
        return notifyChannelCredentialRepository.findAll().map { it.credential.readJsonString() }
    }

    @Transactional(rollbackFor = [Throwable::class], readOnly = true)
    fun get(id: String): NotifyChannelCredential {
        return notifyChannelCredentialRepository.findById(id)?.credential?.readJsonString()
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
    }

    @Transactional(rollbackFor = [Throwable::class], readOnly = true)
    fun listDefault(type: String): List<NotifyChannelCredential> {
        return notifyChannelCredentialRepository.findByTypeAndDefault(type).map { it.credential.readJsonString() }
    }
}
