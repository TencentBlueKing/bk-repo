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

const val REPO_TYPE = "SKILL"

// Slug constraints
const val SLUG_PATTERN = "^[a-z0-9][a-z0-9-]*$"
const val SLUG_MAX_LENGTH = 64

// fields
const val FIELD_DISPLAY_NAME = "displayName"
const val FIELD_CHANGELOG = "changelog"

const val KEY_FILE_LIST = "fileList"
const val KEY_FINGERPRINT = "fingerprint"
const val KEY_SKILL_MD = "skill_md"

// File names
const val SKILL_MD = "SKILL.md"

// Frontmatter fields
const val FRONTMATTER_NAME = "name"
const val FRONTMATTER_DESCRIPTION = "description"

// Limits
const val SKILL_MD_PARSING_MAX_SIZE = 5 * 1024 * 1024L // 5MB
const val SKILL_MD_SHOWING_MAX_SIZE = 1 * 1024 * 1024L
const val DEFAULT_PAGE_LIMIT = 25
const val MAX_PAGE_LIMIT = 200
const val MAX_SEARCH_LIMIT = 100

const val MAX_UPLOAD_TOTAL_SIZE = 50 * 1024 * 1024L // 50MB 上传文件总大小限制（未压缩的大小总和）
const val MAX_UPLOAD_FILE_COUNT = 1000 // 上传单个skill制品包含文件数量限制
const val MAX_EXTRACT_FILE_SIZE = 1 * 1024 * 1024L // 1MB 提取skill压缩包内的文件大小限制

// Sort fields
const val SORT_UPDATED = "updated"
const val SORT_CREATED = "createdAt"
const val SORT_NEWEST = "newest"
const val SORT_DOWNLOADS = "downloads"

// properties
const val LATEST = "latest"
const val MODERATION_VERDICT_CLEAN = "clean"
const val DOWNLOADS = "downloads"
const val VERSIONS = "versions"
const val PACKAGE_KEY = "packageKey"
const val VERSION = "version"

/**
 * 文本文件扩展名集合，与ClawHub客户端TEXT_FILE_EXTENSION_SET保持一致
 * https://github.com/openclaw/clawhub/blob/main/packages/schema/src/textFiles.ts
 */
val TEXT_FILE_EXTENSIONS: Set<String> = setOf(
    "md",
    "mdx",
    "txt",
    "json",
    "json5",
    "yaml",
    "yml",
    "toml",
    "js",
    "cjs",
    "mjs",
    "ts",
    "tsx",
    "jsx",
    "py",
    "sh",
    "ps1",
    "psm1",
    "psd1",
    "r",
    "rb",
    "go",
    "rs",
    "swift",
    "kt",
    "java",
    "cs",
    "cpp",
    "c",
    "h",
    "hpp",
    "sql",
    "csv",
    "tsv",
    "ini",
    "cfg",
    "conf",
    "env",
    "properties",
    "dat",
    "xml",
    "html",
    "css",
    "scss",
    "sass",
    "svg",
)
