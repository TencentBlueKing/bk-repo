package com.tencent.bkrepo.pypi.artifact.html

import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class SchduleJob {
    @Scheduled(cron = "* 0/10 * * * ?")
    private fun cacheJob() {
        // PypiRemoteRepository().cacheRemoteRepoList()
    }
}
