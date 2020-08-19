package com.tencent.bkrepo.rpm.exception

import java.lang.RuntimeException

class RpmIndexTypeResolveException(error: String) : RuntimeException(error)
