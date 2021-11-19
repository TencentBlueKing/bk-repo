package com.tencent.bkrepo.oci.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import org.springframework.stereotype.Component

@Component
class OciRegistryLocalRepository : LocalRepository()
