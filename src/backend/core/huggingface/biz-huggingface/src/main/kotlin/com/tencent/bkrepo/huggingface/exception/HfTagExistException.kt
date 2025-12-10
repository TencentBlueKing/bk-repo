package com.tencent.bkrepo.huggingface.exception

import com.tencent.bkrepo.common.api.constant.HttpStatus

class HfTagExistException(
    tag: String
) : HfException(HttpStatus.CONFLICT, HfErrorCode.TAG_EXIST, tag)