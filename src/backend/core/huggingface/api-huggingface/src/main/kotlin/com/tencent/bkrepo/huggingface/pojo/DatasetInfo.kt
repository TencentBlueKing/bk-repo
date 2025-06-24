/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.pojo

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class DatasetInfo(
    val _id: String,
    val id: String,
    val author: String,
    val sha: String,
    val lastModified: LocalDateTime,
    val private: Boolean,
    val gated: Boolean,
    val disabled: Boolean,
    val tags: List<String>,
    val citation: String?,
    val description: String?,
    val downloads: Int,
    val likes: Int,
    val cardData: CardData?,
    val siblings: List<RepoSibling>,
    val createdAt: LocalDateTime,
    val usedStorage: Long
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CardData(
    val license: String?,
    @JsonProperty("dataset_info")
    val datasetInfo: DatasetInfoDetail?,
    val configs: List<Config>?
)

data class Config(
    @JsonProperty("config_name")
    val configName: String,
    @JsonProperty("data_files")
    val dataFiles: List<DataFile>,
    val default: Boolean? = null
)

data class DataFile(
    val split: String,
    val path: String
)

data class DatasetInfoDetail(
    val features: List<Feature>,
    val splits: List<Split>,
    @JsonProperty("download_size")
    val downloadSize: Long,
    @JsonProperty("dataset_size")
    val datasetSize: Long,
)

data class Feature(
    val name: String,
    val dtype: String,
)

data class Split(
    val name: String,
    @JsonProperty("num_bytes")
    val numBytes: Long,
    @JsonProperty("num_examples")
    val numExamples: Long,
)
