/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.core.crypto

/**
 * 加密工厂生产者，负责生产各类算法的加密工厂
 * */
object CipherFactoryProducer {
    private val mapping = mutableMapOf<String, AbstractCipherFactory>()

    init {
        register("SM4", SM4Factory())
    }

    /**
     * 获取工厂
     * @param algorithm 加密算法
     * @return 加密工厂
     * */
    fun getFactory(algorithm: String): AbstractCipherFactory {
        return mapping[algorithm] ?: throw IllegalArgumentException("$algorithm not supported.")
    }

    /**
     * 注册工厂
     * @param algorithm 加密算法
     * @param factory 加密工厂
     * */
    fun register(algorithm: String, factory: AbstractCipherFactory) {
        mapping[algorithm] = factory
    }
}
