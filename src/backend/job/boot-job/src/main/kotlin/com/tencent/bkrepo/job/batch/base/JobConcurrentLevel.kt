package com.tencent.bkrepo.job.batch.base

/**
 * 任务并发级别
 * */
enum class JobConcurrentLevel {
    /**
     * 序列化
     * */
    SERIALIZE,

    /**
     * 行级别
     * */
    ROW,

    /**
     * 表级别
     * */
    COLLECTION;
}
