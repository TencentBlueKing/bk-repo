/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.proxy.ProxyCreateRequest
import com.tencent.bkrepo.auth.pojo.proxy.ProxyInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyKey
import com.tencent.bkrepo.auth.pojo.proxy.ProxyListOption
import com.tencent.bkrepo.auth.pojo.proxy.ProxyStatusRequest
import com.tencent.bkrepo.auth.pojo.proxy.ProxyUpdateRequest
import com.tencent.bkrepo.common.api.pojo.Page

/**
 * Proxy服务接口
 */
interface ProxyService {

    /**
     * 创建Proxy
     */
    fun create(request: ProxyCreateRequest): ProxyInfo

    /**
     * 查询Proxy信息
     */
    fun getInfo(projectId: String, name: String): ProxyInfo

    /**
     * 分页查询Proxy信息
     */
    fun page(projectId: String, option: ProxyListOption): Page<ProxyInfo>

    /**
     * 查询Proxy加密存储的密钥
     */
    fun getEncryptedKey(projectId: String, name: String): ProxyKey

    /**
     * 更新Proxy
     */
    fun update(request: ProxyUpdateRequest): ProxyInfo

    /**
     * 删除Proxy
     */
    fun delete(projectId: String, name: String)

    /**
     * 获取ticket
     */
    fun ticket(projectId: String, name: String): Int

    /**
     * Proxy开机认证
     */
    fun startup(request: ProxyStatusRequest): String

    /**
     * Proxy关机
     */
    fun shutdown(request: ProxyStatusRequest)

    /**
     * Proxy上报心跳
     */
    fun heartbeat(projectId: String, name: String)
}
