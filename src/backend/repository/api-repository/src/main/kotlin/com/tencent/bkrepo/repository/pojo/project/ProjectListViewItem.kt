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

package com.tencent.bkrepo.repository.pojo.project

import com.tencent.bkrepo.common.artifact.path.PathUtils.UNIX_SEPARATOR
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ProjectListViewItem(
    val name: String,
    val createdBy: String,
    val lastModified: String,
    val shardingIndex: String
) : Comparable<ProjectListViewItem> {

    override fun compareTo(other: ProjectListViewItem): Int {
        return this.name.compareTo(other.name)
    }

    companion object {
        private val formatters = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun from(projectInfo: ProjectInfo): ProjectListViewItem {
            with(projectInfo) {
                val normalizedName = name + UNIX_SEPARATOR
                val localDateTime = LocalDateTime.parse(lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
                val lastModified = formatters.format(localDateTime)
                val shardingIndex = name.hashCode() and SHARDING_COUNT - 1
                return ProjectListViewItem(normalizedName, lastModified, createdBy, shardingIndex.toString())
            }
        }
    }
}
