package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamOmitField

open class RpmXmlMetadata(
    @XStreamOmitField
    open val packages: List<RpmXmlPackage>,
    @XStreamOmitField
    open var packageNum: Long
)
