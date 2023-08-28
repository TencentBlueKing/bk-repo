package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.pojo.execution.ExecutionCluster

/**
 * 创建或更新目标集群参数，并让目标集群主动拉取任务
 */
abstract class SubtaskPullDispatcher<T : ExecutionCluster>(
    executionCluster: T
) : AbsSubtaskDispatcher<T>(executionCluster)
