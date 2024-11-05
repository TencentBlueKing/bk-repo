package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.pojo.execution.ExecutionCluster

abstract class AbsSubtaskDispatcher<T: ExecutionCluster>(
    protected val executionCluster: T
) : SubtaskDispatcher {
    override fun name() = executionCluster.name
}
