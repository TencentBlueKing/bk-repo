package com.tencent.bkrepo.job

/**
 * 分表数量
 */
const val SHARDING_COUNT = 256

/**
 * mongodb 最小id
 */
const val MIN_OBJECT_ID = "000000000000000000000000"

/**
 * 一次处理数据量
 */
const val BATCH_SIZE = 10000
/**
 * 数据库字段
 */
const val ID = "_id"
const val SHA256 = "sha256"
const val PROJECT = "projectId"
const val REPO = "repoName"
const val FOLDER = "folder"
const val CREDENTIALS = "credentialsKey"
const val COUNT = "count"
