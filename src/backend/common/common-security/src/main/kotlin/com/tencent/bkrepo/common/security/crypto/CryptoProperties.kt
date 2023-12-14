/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.security.crypto

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("security.crypto")
data class CryptoProperties(
    var rsaAlgorithm: String = "RSA/ECB/PKCS1Padding",
    // 公私钥必须配置，不然多实例部署时会存在无法解析出加密内容的问题
    var privateKeyStr: String = "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAMaoDhrj" +
        "+Da2tGpawrE8et6vHBjprVj0UiCEza7JVymYTo9gd/pxNJRnbf6NehUL1WP8D6f5e2XZEDNfqXOqyEjPqOKtWIYI6ZLQeQIuAXgyGE5aP3" +
        "/KVHFnxk+IuzcJtvqTAthfeuVXGel9ATP8hlEyDuCJe7/orBjIVYFk3p+PAgMBAAECgYAGYwLJFIk3YRpdzPszbYlZvXF" +
        "+z4x2LqyxRPPD6c82lCH6dBSHZbpWBxk/NNc29AFxTHpIYTn5ZUgjDrFI+bWkqxvgqWS/oyfB6rxajIQjTeorsGvt" +
        "/oumxQA7hvUE2XXLi218RXCURWgz/FZnvNhGhPYUOJWHoPeNlVx3V5mG8QJBAOzP9iSPcw1YJkv6uAgY4MRv1GqPu3NcMif" +
        "+DQVPOZCNPq7ynSg15Zl3HMpl6jAZNJ/AUXRby3tLhO8WiWr6C8cCQQDWwKbhy4AZ5SDigFIPtk" +
        "/655Uzprm2JaZSvGkBeOSB9EYCUC1ApKeImrufPZpSj3Ood/fbMyA6cl8Bswl2z335AkBTNa" +
        "+ToSQYKEUspWhM0BEKdRD6cI65NkgZbVc96lybwkWoS2+VVXrbtdLT+4OSawjmqTj13dtd82c" +
        "+a3jVsg65AkEAn1kiO0caDZzj8s2OlpQL8rwmDMZ45Lw5FwkwzWPcAsWzsQG3IlFK8uUFtRoryXkiM" +
        "+6Y3nCoSFYXQxaLPjqmWQJBAI2tn28XAHFcSd0UnS8L6exJuMdjCw4huI5" +
        "FOeZ0arf5NrWDFoKU30Crw2ozmRBcDvtjDVH9sn8oLC2ObCFItlM=",
    var publicKeyStr: String = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDGqA4a4" +
        "/g2trRqWsKxPHrerxwY6a1Y9FIghM2uyVcpmE6PYHf6cTSUZ23+jXoVC9Vj/A+n+Xtl2RAzX6lzqshIz6jirViGCOmS0HkCLgF4MhhOWj9" +
        "/ylRxZ8ZPiLs3Cbb6kwLYX3rlVxnpfQEz/IZRMg7giXu/6KwYyFWBZN6fjwIDAQAB",

    var privateKeyStr2048PKCS8: String = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCub16CL5c2snVi" +
        "EC1JKi183lRHhEATysjIc0Glok8JYIOI5jBO5v6J+L5QaX8E1Bxlry3ajEsrYWwN" +
        "GyXCFz4boSIOUFtkawi2pdVR/VRyTQR77bRLvBqqsO76Ro6h7CMXBkkbFerqCaU7" +
        "qz8f2GbQM/D5S0obwUEMT2GaXzkC/FCKfnjvnxzM+dpRSQCf1YneKkdhQZKFTVUV" +
        "0psstbx3mZCrYa31tDzTw9vtD6yd38e83+Np+PpVrIq923GqTOJBm1HrqbOPJ8PQ" +
        "7F7jwpQqQ7EMsGwJ1L7YVMW5ubwmLqctwcGNo47XB/Tcfp1CIQaTDnRoTuqQ1qyG" +
        "lnf7lDM9AgMBAAECggEAaHCH7nyeFgK9o2w6IQ9e0t+vKG63iuIkW1ge2xQWLHAb" +
        "8TCZhfRqPKOxFyZDBdoV8o/zbWIIc73N2v8BGXefGR3d7SIRxksLmYgq8/8wu4r/" +
        "f0/wXlQOx1pOi40JQ0vGKrf5t3zk/SGvS82ZavC/hjNDOY/pnDqWPs+cibgvw2fe" +
        "20R1IR73EdXnEcEkorGRXqIwpmWciRj451wYqbdT0ihQgrZwa56bOxL8vD9N6Cyx" +
        "Obec6HBApKJu4OpZhq5UaMXZPE6Rzjy6blf5zNwZZljo+7yAYRb1cbYkGtWyTBvT" +
        "gJgTZbbebUQxfAl9vZYO380o9Tsj+Eclqv5TlQiQAQKBgQDk1Eh6jZOUnAcdwY0S" +
        "FOibigfdsZrWdiQMQcnqbJmnHalu5hxHO6Dcml8DHHYzkwqIQ14y8aKNK4HtQr/e" +
        "0M+4YFdyarn2YtUH4XVaMiV6L8v3cMBhAKJ7G9gGEpTY9Iy1jhoviTITtUxeUWwW" +
        "+f9ez2Qc4CMReAubuQt8Ft7EAQKBgQDDJaq9gNucb9WZt+bYTZJDzMMD6ZwiIzTi" +
        "ZbsKSefeCw3Rnx/4tdC09H3DiBjAOvvPNh64ikIhHR/O0GO4lA9mtCLXZKYvbmwO" +
        "cGD/nNJr4sUhryk9RI6fg2QRr/dLyjRzuZWNAhlxjbCcDfxMXdusso1qYKdu41Bx" +
        "8+KcuUN/PQKBgQCSzCiPDmId1RavnSpd7jHnDl7LdxOo/3NStaXOEMtlrR6z+UUs" +
        "4XDp9NJ9EXY20d7Q2b8FmYQ5Yi7gwZCLZZPMaWnQCe2wxWh6vMVnDoKCZ0VHQPr5" +
        "f8m8hnlINAVvRTs7gaUE19PbVtReMYfRGaIc9Zj4+UUmAMgZp1VZzuYsAQKBgDo9" +
        "eJp75Y7nlYD98IgnhnpzltQJGU7a4QKcR9kHO4r6E5K3AcyxPmty+EGt0W01bUdn" +
        "KH38zUWisoZ/jPNeRMZrBmbwI+TN+LPKeDhxLh+Cm0C1TQJ6/nG+vdPFh3F4FHVh" +
        "Mq/Vq5BHMCkyx1RnQpNk6m2QEQg4ER8hIUWSyQElAoGBANTpY5npwCDTopEDXcmH" +
        "8eZIQbMU69UGzLiIIvfgPdHcFIpDZ0FfHdeL21QeCdnySI6reXQ8l1dd555fMT7K" +
        "BdsQJqmRZzVQiDMQR7DVe+b5WI98KMAV74VVojoZ6jVwypIqlimezJqPP4Z/SkJK" +
        "WGOwDexLmIqrfXE8IU6bQz3Y",
    var publicKeyStr2048PKCS8: String = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArm9egi+XNrJ1YhAtSSot" +
        "fN5UR4RAE8rIyHNBpaJPCWCDiOYwTub+ifi+UGl/BNQcZa8t2oxLK2FsDRslwhc+" +
        "G6EiDlBbZGsItqXVUf1Uck0Ee+20S7waqrDu+kaOoewjFwZJGxXq6gmlO6s/H9hm" +
        "0DPw+UtKG8FBDE9hml85AvxQin54758czPnaUUkAn9WJ3ipHYUGShU1VFdKbLLW8" +
        "d5mQq2Gt9bQ808Pb7Q+snd/HvN/jafj6VayKvdtxqkziQZtR66mzjyfD0Oxe48KU" +
        "KkOxDLBsCdS+2FTFubm8Ji6nLcHBjaOO1wf03H6dQiEGkw50aE7qkNashpZ3+5Qz" +
        "PQIDAQAB"
)
