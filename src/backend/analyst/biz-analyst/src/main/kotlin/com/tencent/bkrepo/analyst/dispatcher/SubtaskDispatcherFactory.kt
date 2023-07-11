package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.pojo.execution.DockerExecutionCluster
import com.tencent.bkrepo.analyst.pojo.execution.KubernetesDeploymentExecutionCluster
import com.tencent.bkrepo.analyst.pojo.execution.KubernetesJobExecutionCluster
import com.tencent.bkrepo.analyst.service.ExecutionClusterService
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration
import com.tencent.bkrepo.statemachine.StateMachine
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class SubtaskDispatcherFactory(
    private val scannerProperties: ScannerProperties,
    private val executionClusterService: ExecutionClusterService,
    private val subScanTaskDao: SubScanTaskDao,
    private val scanService: ScanService,
    private val scannerService: ScannerService,
    @Qualifier(TaskStateMachineConfiguration.STATE_MACHINE_ID_SUB_SCAN_TASK)
    private val subtaskStateMachine: StateMachine,
    private val temporaryScanTokenService: TemporaryScanTokenService,
    private val redisTemplate: ObjectProvider<RedisTemplate<String, String>>
) {
    fun create(executionClusterName: String): SubtaskDispatcher {
        val executionCluster = executionClusterService.get(executionClusterName)
        return when (executionCluster.type) {
            KubernetesJobExecutionCluster.type -> {
                KubernetesDispatcher(
                    executionCluster as KubernetesJobExecutionCluster,
                    scannerProperties,
                    scanService,
                    subtaskStateMachine,
                    temporaryScanTokenService
                )
            }

            KubernetesDeploymentExecutionCluster.type -> {
                KubernetesDeploymentDispatcher(
                    executionCluster as KubernetesDeploymentExecutionCluster,
                    scannerProperties,
                    temporaryScanTokenService,
                    subScanTaskDao,
                    scannerService,
                )
            }

            DockerExecutionCluster.type -> {
                DockerDispatcher(
                    executionCluster as DockerExecutionCluster,
                    scannerProperties,
                    scanService,
                    subtaskStateMachine,
                    temporaryScanTokenService,
                    subScanTaskDao,
                    redisTemplate
                )
            }

            else -> throw IllegalArgumentException("unknown execution cluster type[${executionCluster.type}]")
        }
    }
}
