/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.skill.constant

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * Skill仓库错误码
 */
enum class SkillMessageCode(private val key: String) : MessageCode {
    SKILL_SLUG_INVALID("skill.slug.invalid"),
    SKILL_VERSION_INVALID("skill.version.invalid"),
    SKILL_MD_MISSING("skill.md-missing"),
    SKILL_FILE_TOO_LARGE("skill.file.too-large"),
    SKILL_TOO_MANY_FILES("skill.too-many-files"),
    SKILL_PUBLISH_PAYLOAD_INVALID("skill.publish.payload.invalid"),
    SKILL_PUBLISH_PAYLOAD_MISSING("skill.publish.payload.missing"),
    ;

    override fun getBusinessCode() = ordinal + 1
    override fun getKey() = key
    override fun getModuleCode() = 30
}
