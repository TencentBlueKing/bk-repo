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

package com.tencent.bkrepo.common.artifact.manager.sign

import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.common.metadata.interceptor.DownloadInterceptor
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail

/**
 * 已签名仓库下载拦截器
 *
 * 禁止通过api方式下载水印加固后的文件，只能通过NodeForwardService转发
 */
class SignedRepoDownloadInterceptor(
    private val signProperties: SignProperties,
    private val serviceUserClient: ServiceUserClient
) : DownloadInterceptor<Unit, NodeDetail>(emptyMap()) {
    override fun parseRule() {
        return
    }

    override fun matcher(
        artifact: NodeDetail,
        rule: Unit
    ): Boolean {
        if (artifact.repoName != signProperties.signedRepoName) {
            return true
        }
        if (serviceUserClient.userInfoById(SecurityUtils.getUserId()).data?.admin == true) {
            return true
        }

        return false
    }
}