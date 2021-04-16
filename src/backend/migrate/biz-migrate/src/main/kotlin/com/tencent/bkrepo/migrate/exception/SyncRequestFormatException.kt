package com.tencent.bkrepo.migrate.exception

import java.lang.RuntimeException

class SyncRequestFormatException(override val message: String) : RuntimeException()
