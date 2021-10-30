package com.tencent.bkrepo.executor.util

import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.okhttp.OkDockerHttpClient
import com.tencent.bkrepo.executor.config.container.ContainerTaskConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DockerUtil @Autowired constructor(var config: ContainerTaskConfig) {

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

    fun runContainerOnce(workDir: String): Boolean {
        try {
            val volumeWs = Volume(config.containerDir)
            val binds = Binds(Bind(workDir, volumeWs))
            val containerId = httpDockerCli.createContainerCmd(config.imageName)
                .withHostConfig(HostConfig().withBinds(binds))
                .withCmd(config.args)
                .withTty(true)
                .withStdinOpen(true)
                .exec().id
            logger.info("run container instance Id [$containerId]")
            httpDockerCli.startContainerCmd(containerId).exec()

            val resultCallback = WaitContainerResultCallback()
            httpDockerCli.waitContainerCmd(containerId).exec(resultCallback)
            resultCallback.awaitCompletion()
            httpDockerCli.removeContainerCmd(containerId).withForce(true).exec()
            logger.warn("task docker run success [$workDir, $containerId]")
            return true
        } catch (e: Exception) {
            logger.warn("exec docker task exception[$workDir, $e]")
        }
        return false
    }

    private val logger = LoggerFactory.getLogger(DockerUtil::class.java)
}
