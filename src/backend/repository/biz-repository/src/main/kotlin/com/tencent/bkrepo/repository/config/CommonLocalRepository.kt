package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import org.springframework.stereotype.Component

/**
 * 公共local仓库
 * @author: carrypan
 * @date: 2019/12/20
 */
@Component
class CommonLocalRepository : LocalRepository()
