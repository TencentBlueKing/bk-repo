package com.tencent.bkrepo.analyst.service

import com.tencent.bkrepo.analyst.pojo.execution.ExecutionCluster

interface ExecutionClusterService {
    fun create(executionCluster: ExecutionCluster): ExecutionCluster
    fun remove(name: String)
    fun update(executionCluster: ExecutionCluster): ExecutionCluster
    fun list(): List<ExecutionCluster>
}
