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

package com.tencent.bkrepo.common.artifact.audit

import com.tencent.bk.audit.filter.AuditPostFilter
import com.tencent.bk.audit.model.AuditEvent
import com.tencent.bkrepo.common.api.constant.AUDITED_UID
import com.tencent.bkrepo.common.api.constant.AUDIT_REQUEST_KEY
import com.tencent.bkrepo.common.api.constant.AUDIT_REQUEST_URI
import com.tencent.bkrepo.common.api.constant.AUDIT_SHARE_USER_ID
import com.tencent.bkrepo.common.api.constant.HTTP_METHOD
import com.tencent.bkrepo.common.api.constant.HTTP_RESPONSE_CODE
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.springframework.http.HttpHeaders
import java.util.Locale

class BkAuditPostFilter : AuditPostFilter {
    override fun map(auditEvent: AuditEvent): AuditEvent {
        auditEvent.scopeType = PROJECT_RESOURCE
        try {
            auditEvent.addExtendData(HTTP_RESPONSE_CODE, HttpContextHolder.getResponse().status)
            auditEvent.addExtendData(HTTP_METHOD, HttpContextHolder.getRequest().method)
            auditEvent.addExtendData(
                HttpHeaders.RANGE.lowercase(Locale.getDefault()),
                HttpContextHolder.getRequest().getHeader(HttpHeaders.RANGE)
            )
        } catch (ignore: Exception) {
        }
        // 特殊处理, 使用token下载时下载用户是根据token去判断后塞入httpAttribute中, 初始化时无法获取
        if (auditEvent.extendData.isNullOrEmpty()) return auditEvent
        auditEvent.extendData[AUDIT_SHARE_USER_ID] ?: return auditEvent
        val auditedUid = auditEvent.extendData[AUDITED_UID]?.toString()
        val auditRequestUri = auditEvent.extendData[AUDIT_REQUEST_URI]
        auditEvent.username = auditedUid ?: auditEvent.username
        auditEvent.addExtendData(AUDIT_REQUEST_KEY, auditRequestUri)
        return auditEvent
    }
}