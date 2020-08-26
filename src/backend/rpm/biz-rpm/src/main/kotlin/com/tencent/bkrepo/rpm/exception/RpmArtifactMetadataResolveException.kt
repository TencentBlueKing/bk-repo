package com.tencent.bkrepo.rpm.exception

import java.lang.RuntimeException

class RpmArtifactMetadataResolveException(error: String) : RuntimeException(error)
