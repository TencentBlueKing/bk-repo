package com.tencent.bkrepo.helm.event

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.repo.RepositoryCleanEvent
import com.tencent.bkrepo.helm.constants.REPO_TYPE
import com.tencent.bkrepo.helm.service.ServiceHelmClientService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.stereotype.Component
import java.util.function.Consumer

@Component("artifactEvent")
class EventConsumer(
    private val service: ServiceHelmClientService
) : Consumer<ArtifactEvent> {
    override fun accept(event: ArtifactEvent) {
        require(event.type == EventType.REPOSITORY_CLEAN) { return }
        with(event) {
            require(data[RepositoryCleanEvent::packageType.name] == REPO_TYPE) { return }
            val versionList = data[RepositoryCleanEvent::versionList.name] as List<String>
            versionList.forEach {
                service.deleteVersion(
                    projectId,
                    repoName,
                    data[RepositoryCleanEvent::packageKey.name] as String,
                    it,
                    SYSTEM_USER
                )
            }
        }
    }
}
