package com.tencent.bkrepo.executor.model

import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.okhttp.OkDockerHttpClient
import com.tencent.bkrepo.executor.config.container.ContainerTaskConfig
import com.tencent.bkrepo.executor.exception.RunContainerFailedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DockerRunTime @Autowired constructor(val config: ContainerTaskConfig) {

    private val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerConfig(config.dockerHost)
        .withApiVersion(config.apiVerion)
        .build()

    private val longHttpClient = OkDockerHttpClient.Builder()
        .dockerHost(dockerConfig.dockerHost)
        .sslConfig(dockerConfig.sslConfig)
        .connectTimeout(5000)
        .readTimeout(300000)
        .build()

    private val httpDockerCli = DockerClientBuilder
        .getInstance(dockerConfig)
        .withDockerHttpClient(longHttpClient)
        .build()

    /**
     * 调起容器执行一次性任务
     * @param workDir  工作目录
     * @throws RunContainerFailedException
     */
    fun runContainerOnce(workDir: String) {
        val bind = Volume(config.containerDir)
        val binds = Binds(Bind(workDir, bind))
        val containerId = httpDockerCli.createContainerCmd(config.imageName)
            .withHostConfig(HostConfig().withBinds(binds))
            .withCmd(config.args)
            .withTty(true)
            .withStdinOpen(true)
            .exec().id
        logger.info("run container instance Id [$workDir, $containerId]")
        try {
            httpDockerCli.startContainerCmd(containerId).exec()
            val resultCallback = WaitContainerResultCallback()
            httpDockerCli.waitContainerCmd(containerId).exec(resultCallback)
            resultCallback.awaitCompletion()
            logger.info("task docker run success [$workDir, $containerId]")
        } catch (e: Exception) {
            logger.warn("exec docker task exception[$workDir, $e]")
            throw RunContainerFailedException("exec docker task exception")
        } finally {
            httpDockerCli.removeContainerCmd(containerId).withForce(true).exec()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DockerRunTime::class.java)
    }
}
