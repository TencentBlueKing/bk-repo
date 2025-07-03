/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.pojo.configuration.remote

/**
 * 远程仓库 缓存配置
 */
data class RemoteCacheConfiguration(
    /**
     * 是否开启缓存
     */
    var enabled: Boolean = true,
    /**
     * 构件缓存时间，单位分钟，默认情况下0或负数表示永久缓存（兼容历史仓库类型）
     *
     * 为了减少网络请求数量，新增远程仓库类型时推荐按照此优先级对缓存进行使用：
     * 1. 对于内容不会改变的制品文件（例如某个版本的二进制文件），无需判断过期状态，优先读取缓存；
     * 2. 对于内容可能改变的制品文件（通常是索引文件），在未设置[expiration]值时优先通过网络请求获取最新文件，
     *    可通过覆写RemoteRepository的isExpiredForNonPositiveValue方法更改[expiration]值为0或负数时的定义；
     *    设置了有效的[expiration]值时根据缓存过期状态决定优先级：未过期缓存 > 网络请求 > 已过期缓存，
     *    无法从网络请求获取最新文件时回落到已过期缓存。
     */
    var expiration: Long = -1L
)
