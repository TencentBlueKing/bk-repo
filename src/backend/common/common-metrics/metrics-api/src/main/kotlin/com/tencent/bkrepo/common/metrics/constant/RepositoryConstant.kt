package com.tencent.bkrepo.common.metrics.constant
/**
 * 仓库级别指标常量
 */

// 仓库制品个数指标
const val REPOSITORY_ARTIFACT_COUNT = "bkrepo_artifact_count"
const val REPOSITORY_ARTIFACT_COUNT_DESC = "Number of artifacts in repository"

// 仓库制品用量指标
const val REPOSITORY_ARTIFACT_SIZE_BYTES = "bkrepo_artifact_size_bytes"
const val REPOSITORY_ARTIFACT_SIZE_BYTES_DESC = "Total size of artifacts in repository in bytes"

// 仓库配额指标
const val REPOSITORY_QUOTA_BYTES = "bkrepo_repository_quota_bytes"
const val REPOSITORY_QUOTA_BYTES_DESC = "Repository quota in bytes, -1 means no quota configured"

// 指标标签常量
const val LABEL_PROJECT = "project"
const val LABEL_REPOSITORY = "repository"
const val LABEL_REPO_TYPE = "repo_type"
