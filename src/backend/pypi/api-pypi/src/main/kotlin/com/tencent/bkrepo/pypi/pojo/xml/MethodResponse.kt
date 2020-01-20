package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.annotations.XStreamAlias

@XStreamAlias("methodResponse")
class MethodResponse(
    val params: Params
)
