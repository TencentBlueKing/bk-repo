package com.tencent.bkrepo.rpm.util.xStream.pojo

import com.thoughtworks.xstream.annotations.XStreamOmitField

open class RpmXmlPackage(
    @XStreamOmitField
    open val pkgid: String,
    @XStreamOmitField
    open val name: String,
    @XStreamOmitField
    open val version: RpmVersion
)
