package com.tencent.bkrepo.git.listener

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.git.context.DfsDataReaders
import com.tencent.bkrepo.git.context.DfsDataReadersHolder
import com.tencent.bkrepo.git.context.UserHolder
import com.tencent.bkrepo.git.exception.LockFailedException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.TagOpt
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ArrayBlockingQueue
import kotlin.system.measureNanoTime

@Component
class SyncRepositoryListener {

    private val taskQueue = ArrayBlockingQueue<SyncRepositoryEvent>(MAX_TASK_SIZE)

    @EventListener(SyncRepositoryEvent::class)
    fun listen(event: SyncRepositoryEvent) {
        logger.info("Receive repo sync event ${event.db.identifier}.")
        if (taskQueue.contains(event)) {
            return
        }
        syncRepoWithLock(event)
    }

    @Scheduled(fixedDelay = TASK_INTERVAL)
    fun replay() {
        var done = 0
        var task = taskQueue.poll()
        while (task != null) {
            syncRepoWithLock(task)
            task = taskQueue.poll()
            done++
        }
        if (done > 0) {
            logger.info("Done task count $done.")
        }
    }

    private fun syncRepoWithLock(event: SyncRepositoryEvent) {
        val db = event.db
        try {
            db.lock()
            DfsDataReadersHolder.setDfsReader(DfsDataReaders())
            UserHolder.setUser(event.user)
            logger.info("Start sync db[${db.identifier}].")
            val nanoTime = measureNanoTime { syncRepo(event) }
            logger.info("End sync db[${db.identifier}], elapse ${HumanReadable.time(nanoTime)}.")
        } catch (e: LockFailedException) {
            if (!taskQueue.contains(event)) {
                taskQueue.put(event)
            }
            logger.warn("Not obtain lock: ${e.message}.")
        } catch (e: TransportException) {
            val errorMessage = e.message.orEmpty()
            if (errorMessage.contains("Authentication is required", ignoreCase = true)) {
                logger.warn("Authentication failed for repository ${db.identifier}, ${e.message}")
            } else {
                logger.error("Failed to fetch ${db.identifier}", e)
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch ${db.identifier}", e)
        } finally {
            db.unLock()
            DfsDataReadersHolder.reset()
            UserHolder.reset()
        }
    }

    private fun syncRepo(event: SyncRepositoryEvent) {
        with(event) {
            val remoteConfiguration = event.repositoryDetail.configuration as RemoteConfiguration
            val credentials = remoteConfiguration.credentials
            val credentialsProvider: CredentialsProvider? = if (
                credentials.username != null &&
                credentials.password != null
            ) {
                UsernamePasswordCredentialsProvider(
                    credentials.username, credentials.password
                )
            } else null
            val remote = remoteConfiguration.url
            val git = Git(db)
            val fetchCommand = git.fetch()
                .setRemote(remote)
                .setRefSpecs(RefSpec(FETCH_REF_SPECS))
                .setTagOpt(TagOpt.FETCH_TAGS)
            credentialsProvider.let {
                fetchCommand.setCredentialsProvider(credentialsProvider)
            }
            fetchCommand.call()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SyncRepositoryListener::class.java)
        private const val FETCH_REF_SPECS = "+refs/heads/*:refs/heads/*"
        private const val MAX_TASK_SIZE = 8192
        private const val TASK_INTERVAL = 3000L
    }
}
