package com.tencent.bkrepo.scanner.component.manager.scancode.model

import com.tencent.bkrepo.common.scanner.pojo.scanner.scanCodeCheck.result.ScancodeItem
import com.tencent.bkrepo.scanner.component.manager.ResultItem
import org.springframework.data.mongodb.core.mapping.Document

@Document("scancode_item")
class TScancodeItem(
    id: String? = null,
    credentialsKey: String?,
    sha256: String,
    scanner: String,
    data: ScancodeItem
) : ResultItem<ScancodeItem>(id, credentialsKey, sha256, scanner, data)
