package com.tencent.bkrepo.pypi.pojo.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "methodCall")
class PypiMethodCallXml(
    @JacksonXmlProperty(localName = "methodName")
    val methodName: String?,
    @JacksonXmlElementWrapper(localName = "params")
    val params: Params?
) {
    @JacksonXmlRootElement(localName = "params")
    class Params(
        @JacksonXmlElementWrapper(localName = "param")
        val param0: Param0?,
        @JacksonXmlElementWrapper(localName = "param")
        val param1: Param1?
    ){
        @JacksonXmlRootElement(localName = "param")
        class Param0(
            @JacksonXmlElementWrapper(localName = "value")
            val value: Value?
        ) {
            @JacksonXmlRootElement(localName = "value")
            class Value(
                @JacksonXmlElementWrapper(localName = "struct")
                val struct: Struct?
            ) {
                @JacksonXmlRootElement(localName = "struct")
                class Struct(
                    @JacksonXmlElementWrapper(localName = "member")
                    val member0: Member?,
                    @JacksonXmlElementWrapper(localName = "member")
                    val member1: Member?
                ) {
                    @JacksonXmlRootElement(localName = "member")
                    class Member(
                        @JacksonXmlProperty(localName = "name")
                        val name: String?,
                        @JacksonXmlElementWrapper(localName = "value")
                        val value: Value?
                    ) {
                        @JacksonXmlRootElement(localName = "value")
                        class MemberValue(
                            @JacksonXmlElementWrapper(localName = "array")
                            val array: Array?
                        ) {
                            @JacksonXmlRootElement(localName = "value")
                            class Array(
                                @JacksonXmlElementWrapper(localName = "data")
                                val data: Data?
                            ) {
                                @JacksonXmlRootElement(localName = "data")
                                class Data(
                                    @JacksonXmlElementWrapper(localName = "value")
                                    val value: DataValue?
                                ) {
                                    @JacksonXmlRootElement(localName = "value")
                                    class DataValue(
                                        @JacksonXmlProperty(localName = "string")
                                        val string: String?
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        @JacksonXmlRootElement(localName = "param")
        class Param1(
            @JacksonXmlElementWrapper(localName = "value")
            val value: Param1Value
        ) {
            @JacksonXmlRootElement(localName = "value")
            class Param1Value(
                @JacksonXmlProperty(localName = "string")
                val string: String
            )
        }

    }
}