package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * 公共local仓库
 */
@Primary
@Component
class CommonLocalRepository : LocalRepository()
