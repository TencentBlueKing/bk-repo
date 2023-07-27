package com.tencent.bkrepo.oci.event

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.repo.RepositoryCleanEvent
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.oci.controller.service.OciPackageController
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.stereotype.Component
import java.util.function.Consumer

@Component("artifactEvent")
class EventConsumer(
    private val service: OciPackageController
) : Consumer<ArtifactEvent> {
    override fun accept(event: ArtifactEvent) {
        require(event.type == EventType.REPOSITORY_CLEAN) { return }
        with(event) {
            require(
                data[RepositoryCleanEvent::packageType.name] in listOf(
                    RepositoryType.DOCKER.name,
                    RepositoryType.OCI.name
                )) { return }
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
