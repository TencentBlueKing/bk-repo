package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import org.springframework.stereotype.Component

/**
 * 公共远程仓库
 */
@Component
class CommonRemoteRepository : RemoteRepository()
