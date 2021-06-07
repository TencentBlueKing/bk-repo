package com.tencent.bkrepo.replication.pojo.task

/**
 * 排序类型
 */
enum class TaskSortType(val key: String) {
    /**
     * 按照创建时间排序
     */
    CREATED_TIME("createdDate"),

    /**
     * 按照上次执行实际排序
     */
    LAST_EXECUTION_TIME("lastExecutionTime"),

    /**
     * 按照下次执行实际排序
     */
    NEXT_EXECUTION_TIME("nextExecutionTime")
}
