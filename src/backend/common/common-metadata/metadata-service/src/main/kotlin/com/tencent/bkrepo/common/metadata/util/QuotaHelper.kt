package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.event.repo.RepoVolumeSyncEvent
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.model.TRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class QuotaHelper(
    applicationEventPublisher: ApplicationEventPublisher
) {

    init {
        Companion.applicationEventPublisher = applicationEventPublisher
    }

    companion object {
        private lateinit var applicationEventPublisher: ApplicationEventPublisher

        fun checkQuota(
            tRepository: TRepository,
            change: Long
        ) {
            with(tRepository) {
                quota?.let {
                    if (used!! + change < 0) {
                        applicationEventPublisher.publishEvent(RepoVolumeSyncEvent(projectId, name))
                    }
                    if (used!! + change > it) {
                        throw ErrorCodeException(
                            ArtifactMessageCode.REPOSITORY_OVER_QUOTA,
                            name,
                            HumanReadable.size(quota!!)
                        )
                    }
                }
            }
        }
    }


}
