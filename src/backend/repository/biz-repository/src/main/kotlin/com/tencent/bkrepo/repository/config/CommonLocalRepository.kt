package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import org.springframework.stereotype.Component

/**
 * 公共local仓库
 */
@Component
class CommonLocalRepository : LocalRepository()
