package com.tencent.bkrepo.common.storage.innercos.response

import java.io.InputStream

data class CosObject(val eTag: String?, val inputStream: InputStream?)
