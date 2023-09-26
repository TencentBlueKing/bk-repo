package com.tencent.bkrepo.common.artifact.repository.proxy

import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import org.springframework.stereotype.Service

/**
 * 代理仓库
 *
 * 暂时不做任务业务处理，仅用作区分仓库类型
 * */
@Service
class ProxyRepository : AbstractArtifactRepository()
