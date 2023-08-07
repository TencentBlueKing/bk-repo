package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.dispatcher.dsl.addContainerItem
import com.tencent.bkrepo.analyst.dispatcher.dsl.limits
import com.tencent.bkrepo.analyst.dispatcher.dsl.metadata
import com.tencent.bkrepo.analyst.dispatcher.dsl.requests
import com.tencent.bkrepo.analyst.dispatcher.dsl.resources
import com.tencent.bkrepo.analyst.dispatcher.dsl.rollingUpdate
import com.tencent.bkrepo.analyst.dispatcher.dsl.selector
import com.tencent.bkrepo.analyst.dispatcher.dsl.spec
import com.tencent.bkrepo.analyst.dispatcher.dsl.strategy
import com.tencent.bkrepo.analyst.dispatcher.dsl.template
import com.tencent.bkrepo.analyst.dispatcher.dsl.v1Deployment
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.execution.KubernetesDeploymentExecutionCluster
import com.tencent.bkrepo.analyst.pojo.execution.KubernetesExecutionClusterProperties
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.Companion.RUNNING_STATUS
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import io.kubernetes.client.custom.IntOrString
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.AppsV1Api
import io.kubernetes.client.openapi.models.V1Deployment
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import kotlin.math.abs

/**
 * 通过K8S的Deployment执行制品分析任务
 * 每个[executionCluster]只会创建一个对应的Deployment，Deployment会根据任务数量进行扩缩容，所有任务执行结束后会清理Deployment
 */
class KubernetesDeploymentDispatcher(
    executionCluster: KubernetesDeploymentExecutionCluster,
    private val scannerProperties: ScannerProperties,
    private val tokenService: TemporaryScanTokenService,
    private val subScanTaskDao: SubScanTaskDao,
    private val scannerService: ScannerService,
) : SubtaskPullDispatcher<KubernetesDeploymentExecutionCluster>(executionCluster) {

    private val client by lazy { createClient(executionCluster.kubernetesProperties) }
    private val api: AppsV1Api? by lazy { AppsV1Api(client) }

    override fun dispatch() {
        val runningTaskCount = subScanTaskDao.countTaskByStatusIn(RUNNING_STATUS, executionCluster.name).toInt()
        if (runningTaskCount != 0) {
            // 创建deployment
            createOrScaleDeployment(runningTaskCount)
        }
    }

    override fun clean(subtask: SubScanTask, subtaskStatus: String): Boolean {
        val runningTaskCount = subScanTaskDao.countTaskByStatusIn(RUNNING_STATUS, executionCluster.name).toInt()
        if (runningTaskCount > 0) {
            // 尝试减少deployment的副本数量
            getDeployment()?.let { scale(it, targetReplicas(runningTaskCount)) }
        }

        // 不存在属于该分发器的任务时直接删除对应的Deployment
        if (subScanTaskDao.countTaskByStatusIn(null, executionCluster.name) == 0L) {
            val deploymentName = deploymentName()
            api!!.deleteNamespacedDeployment(
                deploymentName, executionCluster.kubernetesProperties.namespace,
                null, null, null, null, "Foreground", null
            )
            tokenService.deleteToken(deploymentName)
            logger.info("delete deployment[$deploymentName] success")
        }

        return true
    }

    private fun createOrScaleDeployment(runningTaskCount: Int): V1Deployment {
        var deployment = getDeployment()
        val targetReplicas = targetReplicas(runningTaskCount)

        // 创建deployment
        if (deployment == null) {
            val scanner = scannerService.get(executionCluster.scanner)
            require(scanner is StandardScanner)
            try {
                return createDeployment(executionCluster.kubernetesProperties, scanner, targetReplicas)
            } catch (e: ApiException) {
                // 创建
                if (e.code == HttpStatus.CONFLICT.value()) {
                    // deployment已存在，无需重复创建
                    deployment = getDeployment()
                } else {
                    logger.error(e.string())
                    throw e
                }
            }
        }

        scale(deployment!!, targetReplicas)
        return deployment
    }

    private fun scale(deployment: V1Deployment, targetReplicas: Int) {
        // 对deployment扩缩容
        if (abs(deployment.spec!!.replicas!! - targetReplicas) > executionCluster.scaleThreshold) {
            logger.info(
                "scale deployment[${deployment.metadata!!.name!!}] " +
                        "from ${deployment.spec!!.replicas} to $targetReplicas"
            )
            deployment.spec!!.replicas = targetReplicas
            // 更新Deployment
            api!!.replaceNamespacedDeployment(
                deployment.metadata!!.name!!,
                deployment.metadata!!.namespace!!,
                deployment,
                null, null, null
            )
            logger.info("scale deployment[${deployment.metadata!!.name}] success")
        }
    }

    private fun getDeployment(): V1Deployment? {
        return try {
            api!!.readNamespacedDeployment(
                deploymentName(),
                executionCluster.kubernetesProperties.namespace,
                null,
                null,
                null
            )
        } catch (e: ApiException) {
            if (e.code == HttpStatus.NOT_FOUND.value()) {
                return null
            }
            throw e
        }
    }

    private fun createDeployment(
        k8sProps: KubernetesExecutionClusterProperties,
        scanner: StandardScanner,
        targetReplicas: Int
    ): V1Deployment {
        val deploymentName = deploymentName()
        val token = tokenService.createExecutionClusterToken(executionCluster.name)
        val cmd = buildCommand(scanner.cmd, token)
        val body = v1Deployment {
            metadata {
                namespace = k8sProps.namespace
                name = deploymentName
                labels = mapOf("app" to deploymentName)
            }
            spec {
                replicas = targetReplicas
                selector {
                    matchLabels = mapOf("app" to deploymentName)
                }
                strategy {
                    type = "RollingUpdate"
                    rollingUpdate {
                        maxSurge = IntOrString(0)
                        maxUnavailable = IntOrString(1)
                    }
                }
                template {
                    metadata {
                        labels = mapOf("app" to deploymentName)
                    }
                    spec {
                        addContainerItem {
                            name = deploymentName
                            image = scanner.image
                            command = cmd
                            resources {
                                limits(
                                    cpu = k8sProps.limitCpu,
                                    memory = k8sProps.limitMem,
                                    ephemeralStorage = k8sProps.limitStorage
                                )
                                requests(
                                    cpu = k8sProps.requestCpu,
                                    memory = k8sProps.requestMem,
                                    ephemeralStorage = k8sProps.requestStorage
                                )
                            }
                        }
                    }
                }
            }
        }
        val deployment = api!!.createNamespacedDeployment(k8sProps.namespace, body, null, null, null)
        logger.info("create deployment[$deploymentName] success")
        return deployment
    }

    private fun buildCommand(cmd: String, token: String): List<String> {
        val command = ArrayList<String>()
        command.addAll(cmd.split(" "))
        command.add("--url")
        command.add(scannerProperties.baseUrl)
        command.add("--token")
        command.add(token)
        command.add("--execution-cluster")
        command.add(executionCluster.name)
        command.add("--pull-retry")
        command.add(executionCluster.pullRetry.toString())
        return command
    }

    /**
     * minReplicas <= targetReplicas <= maxReplicas
     */
    private fun targetReplicas(runningTaskCount: Int) =
        maxOf(minOf(runningTaskCount, executionCluster.maxReplicas), executionCluster.minReplicas)

    private fun deploymentName() = "bkrepo-analyst-${executionCluster.name}-${executionCluster.scanner}"

    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesDeploymentDispatcher::class.java)
    }
}
