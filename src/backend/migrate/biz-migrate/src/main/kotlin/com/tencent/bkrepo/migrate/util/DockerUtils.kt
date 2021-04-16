package com.tencent.bkrepo.migrate.util

import com.tencent.bkrepo.migrate.util.shell.ShellUtils
import org.slf4j.LoggerFactory

object DockerUtils {
    private const val DOCKER_LOGIN = "docker login -u %s -p %s %s"
    private const val DOCKER_LOGOUT = "docker logout %s"
    private const val DOCKER_PULL = "docker pull %s"
    private const val DOCKER_TAG = "docker tag %s %s"
    private const val DOCKER_PUSH = "docker push %s"
    private const val DOCKER_RMI = "docker rmi %s"
    private val logger = LoggerFactory.getLogger(DockerUtils::class.java)

    private fun dockerCli(shellStr: String): Boolean {
        Thread.sleep(500)
        val shellResult = ShellUtils.runShell(shellStr)
        return if (shellResult.isSuccess()) {
            true
        } else {
            logger.error("Docker shell execute failed: $shellStr, exitValue: ${shellResult.exitValue}")
            logger.error("Output content: ${shellResult.outputStr}")
            false
        }
    }

    val retryShell = { shellStr: String ->
        var result = false
        for (i in 1..4) {
            if (dockerCli(shellStr).also { result = it }) break
            logger.info("Will retry $i: $shellStr")
            if (i == 4) break
            Thread.sleep(200)
        }
        result
    }

    fun dockerLogin(username: String, password: String, url: String): Boolean {
        val loginStr = String.format(DOCKER_LOGIN, username, password, url)
        logger.info(loginStr)
        return retryShell(loginStr)
    }

    fun dockerLogout(repoUrl: String): Boolean {
        val logoutStr = String.format(DOCKER_LOGOUT, repoUrl)
        logger.info(logoutStr)
        return dockerCli(logoutStr)
    }

    fun dockerPull(imageUrl: String): Boolean {
        val pullStr = String.format(DOCKER_PULL, imageUrl)
        logger.info(pullStr)
        return retryShell(pullStr)
    }

    fun dockerTag(imageUrl: String, tagUrl: String): Boolean {
        val tagStr = String.format(DOCKER_TAG, imageUrl, tagUrl)
        logger.info(tagStr)
        return dockerCli(tagStr)
    }

    fun dockerPush(tagUrl: String): Boolean {
        val pushStr = String.format(DOCKER_PUSH, tagUrl)
        logger.info(pushStr)
        return retryShell(pushStr)
    }

    fun dockerRmi(tagUrl: String): Boolean {
        val rmiStr = String.format(DOCKER_RMI, tagUrl)
        logger.info(rmiStr)
        return dockerCli(rmiStr)
    }
}
