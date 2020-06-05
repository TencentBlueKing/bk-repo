package com.tencent.bkrepo.rpm.util.redline.model

import org.redline_rpm.header.Format

class FormatWithType : Format() {
    private val leadWithType: LeadWithType = LeadWithType()
    override fun getLead(): LeadWithType {
        return this.leadWithType
    }
}
