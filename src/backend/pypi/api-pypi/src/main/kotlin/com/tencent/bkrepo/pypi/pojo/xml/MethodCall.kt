package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.annotations.XStreamAlias

@XStreamAlias("methodCall")
class MethodCall(
    val methodName: String,
    val params: Params
)
