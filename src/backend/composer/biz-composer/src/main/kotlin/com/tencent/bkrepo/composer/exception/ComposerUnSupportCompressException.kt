package com.tencent.bkrepo.composer.exception

import java.lang.RuntimeException

class ComposerUnSupportCompressException(error: String) : RuntimeException(error)
