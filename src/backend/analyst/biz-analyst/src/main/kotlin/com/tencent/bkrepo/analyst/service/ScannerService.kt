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

package com.tencent.bkrepo.analyst.service

import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner

/**
 * 扫描器服务
 */
interface ScannerService {
    /**
     * 创建扫描器
     *
     * @param scanner 扫描器配置
     */
    fun create(scanner: Scanner): Scanner
    /**
     * 获取扫描器
     *
     * @param name 扫描器名
     * @throws com.tencent.bkrepo.analyst.exception.ScannerNotFoundException 找不到指定扫描器时抛出异常
     */
    fun get(name: String): Scanner

    /**
     * 获取默认扫描器
     */
    fun default(): Scanner

    /**
     * 获取扫描器
     *
     * @param name 扫描器名
     */
    fun find(name: String): Scanner?

    /**
     * 获取扫描器列表
     * @param names 扫描器名称
     */
    fun find(names: List<String>): List<Scanner>

    /**
     * 获取支持扫描指定包类型和扫描类型的扫描器
     *
     * @param packageType 包类型
     * @param scanType 扫描类型
     */
    fun find(packageType: String?, scanType: String?): List<Scanner>

    /**
     * 获取支持扫描的文件名后缀
     */
    fun supportFileNameExt(): Set<String>

    /**
     * 获取支持扫描的包类型
     */
    fun supportPackageType(): Set<String>

    /**
     * 获取所有扫描器
     */
    fun list(): List<Scanner>

    /**
     * 删除扫描器
     */
    fun delete(name: String)

    /**
     * 更新扫描器
     */
    fun update(scanner: Scanner): Scanner
}
