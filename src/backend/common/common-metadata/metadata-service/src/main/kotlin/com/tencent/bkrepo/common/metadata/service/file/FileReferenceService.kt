/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.service.file

import com.tencent.bkrepo.common.metadata.pojo.file.FileReference

/**
 * 文件引用服务接口
 */
interface FileReferenceService {

    /**
     * 增加文件[sha256]在存储实例[credentialsKey]上的引用数量
     *
     * [credentialsKey]为`null`则使用默认的存储实例
     * 增加引用成功则返回`true`
     */
    fun increment(sha256: String, credentialsKey: String?, inc: Long = 1L): Boolean

    /**
     * 减少文件[sha256]在存储实例[credentialsKey]上的文件数量
     *
     * [credentialsKey]为`null`则使用默认的存储实例
     * 减少引用成功则返回`true`，如果当前[sha256]的引用已经为0，返回`false`
     */
    fun decrement(sha256: String, credentialsKey: String?): Boolean

    /**
     * 统计文件[sha256]在存储实例[credentialsKey]上的文件引用数量
     *
     * [credentialsKey]为`null`则使用默认的存储实例
     */
    fun count(sha256: String, credentialsKey: String?): Long

    /**
     * 获取文件文件引用信息
     *
     * @param credentialsKey 文件所在存储实例
     * @param sha256 所引用文件的sha256
     */
    fun get(credentialsKey: String?, sha256: String): FileReference

    /**
     * 判断引用是否存在
     *
     * @param sha256 所引用文件的sha256
     * @param credentialsKey 文件所在存储实例
     *
     * @return 是否存在
     */
    fun exists(sha256: String, credentialsKey: String?): Boolean
}
