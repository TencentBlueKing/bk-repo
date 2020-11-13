package com.tencent.bkrepo.helm.listener

import com.tencent.bkrepo.helm.exception.HelmException
import com.tencent.bkrepo.helm.listener.event.ChartDeleteEvent
import com.tencent.bkrepo.helm.listener.event.ChartVersionDeleteEvent
import com.tencent.bkrepo.helm.utils.HelmUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ChartEventListener : AbstractEventListener() {

    /**
     * 删除chart版本，更新index.yaml文件
     */
    @Synchronized
    @EventListener(ChartVersionDeleteEvent::class)
    fun handle(event: ChartVersionDeleteEvent) {
        // 如果index.yaml文件不存在，说明还没有初始化该文件，return
        // 如果index.yaml文件存在，则进行更新
        event.apply {
            with(request) {
                try {
                    if (!exist(projectId, repoName, HelmUtils.getIndexYamlFullPath())) {
                        logger.warn("Index yaml file is not initialized, return.")
                        return
                    }
                    val originalIndexYamlMetadata = getOriginalIndexYaml()
                    originalIndexYamlMetadata.entries.let {
                        val chartMetadataSet =
                            it[name] ?: throw HelmException("index.yaml file for chart [$name] not found.")
                        if (chartMetadataSet.size == 1 && (version == chartMetadataSet.first().version)) {
                            it.remove(name)
                        } else {
                            run stop@{
                                chartMetadataSet.forEachIndexed { _, helmChartMetadata ->
                                    if (version == helmChartMetadata.version) {
                                        chartMetadataSet.remove(helmChartMetadata)
                                        return@stop
                                    }
                                }
                            }
                        }
                    }
                    uploadIndexYamlMetadata(originalIndexYamlMetadata)
                    logger.info(
                        "User [$operator] fresh index.yaml for delete chart [$name], version [$version] in repo [$projectId/$repoName] success!"
                    )
                } catch (exception: TypeCastException) {
                    logger.error("User [$operator] fresh index.yaml for delete chart [$name], version [$version] in repo [$projectId/$repoName] failed, message: $exception")
                    throw exception
                }
            }
        }
    }

    /**
     * 删除chart版本，更新index.yaml文件
     */
    @Synchronized
    @EventListener(ChartDeleteEvent::class)
    fun handle(event: ChartDeleteEvent) {
        event.apply {
            with(request) {
                try {
                    if (!exist(projectId, repoName, HelmUtils.getIndexYamlFullPath())) {
                        logger.warn("Index yaml file is not initialized, return.")
                        return
                    }
                    val originalIndexYamlMetadata = getOriginalIndexYaml()
                    originalIndexYamlMetadata.entries.remove(name)
                    uploadIndexYamlMetadata(originalIndexYamlMetadata)
                    logger.info(
                        "User [$operator] fresh index.yaml for delete chart [$name] in repo [$projectId/$repoName] success!"
                    )
                } catch (exception: TypeCastException) {
                    logger.error("User [$operator] fresh index.yaml for delete chart [$name] in repo [$projectId/$repoName] failed, message: $exception")
                    throw exception
                }
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ChartEventListener::class.java)
    }
}
