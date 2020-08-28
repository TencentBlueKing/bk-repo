package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class GenericRemoteRepository : RemoteRepository()
