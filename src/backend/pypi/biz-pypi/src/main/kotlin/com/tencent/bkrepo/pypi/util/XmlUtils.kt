package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.artifact.xml.XmlConvertUtil
import com.tencent.bkrepo.pypi.exception.PypiSearchParamException
import com.tencent.bkrepo.pypi.pojo.PypiSearchPojo
import java.io.BufferedReader

object XmlUtils {
    fun BufferedReader.readXml(): String {
        val stringBuilder = StringBuilder("")
        var mark: String?

        while (this.readLine().also { mark = it } != null) {
            stringBuilder.append(mark)
        }
        return stringBuilder.toString()
    }

    fun getPypiSearchPojo(xmlString: String): PypiSearchPojo {
        val methodCall = XmlConvertUtil.xml2MethodCall(xmlString)
        val action = methodCall.methodName
        val name = methodCall.params.paramList[0]
            .value.struct?.memberList?.get(0)?.value?.array?.data?.valueList?.get(0)?.string

        val summary = methodCall.params.paramList[0]
            .value.struct?.memberList?.get(1)?.value?.array?.data?.valueList?.get(0)?.string

        val operation = methodCall.params.paramList[1]
            .value.string ?: throw PypiSearchParamException("can not found `operation` param")
        return PypiSearchPojo(action, name, summary, operation)
    }
}
