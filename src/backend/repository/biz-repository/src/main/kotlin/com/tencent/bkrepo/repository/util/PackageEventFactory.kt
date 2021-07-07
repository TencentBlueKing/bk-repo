package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.artifact.event.packages.VersionCreatedEvent
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest

/**
 * 包版本事件构造类
 */
object PackageEventFactory {

    /**
     * 包版本创建事件
     */
    fun buildCreatedEvent(request: PackageVersionCreateRequest): VersionCreatedEvent {
        with(request) {
            return VersionCreatedEvent(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                packageVersion = versionName,
                userId = createdBy
            )
        }
    }
}
