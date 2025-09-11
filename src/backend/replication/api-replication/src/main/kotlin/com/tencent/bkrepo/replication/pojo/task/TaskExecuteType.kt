package com.tencent.bkrepo.replication.pojo.task

enum class TaskExecuteType {
    DELTA,  // 增量任务
    FULL,   // 全量任务
    PARTIAL; // 部分任务（新增）
}