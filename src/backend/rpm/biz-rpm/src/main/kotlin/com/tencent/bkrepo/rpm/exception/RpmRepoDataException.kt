package com.tencent.bkrepo.rpm.exception

import java.lang.RuntimeException

class RpmRepoDataException(error: String) : RuntimeException(error)
