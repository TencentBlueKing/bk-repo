package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.pypi.artifact.xml.Value
import com.tencent.bkrepo.pypi.artifact.xml.XmlConvertUtil
import com.tencent.bkrepo.pypi.artifact.xml.XmlUtil

/**
 * pypi 单独的接口
 */
interface PypiRepository {
    fun searchNodeList(context: ArtifactSearchContext, xmlString: String): MutableList<Value>?

    fun searchXml(context: ArtifactSearchContext, xmlString: String) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "text/xml; charset=UTF-8"
        val methodResponse = XmlUtil.getEmptyMethodResponse()
        searchNodeList(context, xmlString)?.let {
            methodResponse.params.paramList[0].value.array?.data?.valueList?.addAll(it)
        }
        response.writer.print(XmlConvertUtil.methodResponse2Xml(methodResponse))
    }
}
