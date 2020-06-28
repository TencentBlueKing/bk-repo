package com.tencent.bkrepo.rpm.util.redline.model

import org.redline_rpm.header.Lead
import org.redline_rpm.header.RpmType

class LeadWithType : Lead() {
    fun getType(): RpmType {
        return this.type
    }
}
