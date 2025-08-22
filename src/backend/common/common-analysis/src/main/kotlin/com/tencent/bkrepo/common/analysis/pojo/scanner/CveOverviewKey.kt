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

package com.tencent.bkrepo.common.analysis.pojo.scanner

/**
 * CVE数量预览数据Key
 */
enum class CveOverviewKey(val key: String, val level: Level) {
    CVE_CRITICAL_COUNT("cveCriticalCount", Level.CRITICAL),
    CVE_HIGH_COUNT("cveHighCount", Level.HIGH),
    CVE_MEDIUM_COUNT("cveMediumCount", Level.MEDIUM),
    CVE_LOW_COUNT("cveLowCount", Level.LOW);

    companion object {
        fun overviewKeyOf(level: String): String {
            when (level) {
                Level.CRITICAL.levelName -> CVE_CRITICAL_COUNT.key
                Level.HIGH.levelName -> CVE_HIGH_COUNT.key
                Level.MEDIUM.levelName -> CVE_MEDIUM_COUNT.key
                Level.LOW.levelName -> CVE_LOW_COUNT
            }
            return "cve${level.capitalize()}Count"
        }
    }
}
