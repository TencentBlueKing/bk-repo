package com.tencent.bkrepo.pypi.pojo.xml

import com.thoughtworks.xstream.XStream

object XmlConvertUtil {

    val xStream = XStream()

    init {
        XStream.setupDefaultSecurity(xStream)
        xStream.autodetectAnnotations(true)
        xStream.toXML(MethodCall::class.java)
        xStream.allowTypes(arrayOf(
                MethodCall::class.java,
                Params::class.java,
                Param::class.java,
                Value::class.java,
                Struct::class.java,
                Member::class.java,
                Array::class.java,
                Data::class.java

        ))
        xStream.alias("methodCall", MethodCall::class.java)
        xStream.alias("params", Params::class.java)
        xStream.alias("param", Param::class.java)
        xStream.alias("value", Value::class.java)
        xStream.alias("struct", Struct::class.java)
        xStream.alias("member", Member::class.java)
        xStream.alias("array", Array::class.java)
        xStream.alias("data", Data::class.java)
    }
    /**
     * 转换之前需去掉xmlString中的xml声明
     */
    fun convert(xmlString: String): MethodCall {
        return xStream.fromXML(xmlString) as MethodCall
    }

    fun convert(methodResponse: MethodResponse): String {
        return xStream.toXML(methodResponse)
    }
}
