package com.tencent.bkrepo.rpm.exception

import java.lang.RuntimeException

class RpmVersionNotFoundException(error: String): RuntimeException(error)