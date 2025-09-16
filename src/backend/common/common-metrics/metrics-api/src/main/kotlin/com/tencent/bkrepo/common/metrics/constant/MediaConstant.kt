/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metrics.constant

const val PACKET_RECV_COUNTER = "packet.recv.count"
const val PACKET_RECV_COUNTER_DESC = "包接受数量"
const val PACKET_HANDLER_COUNTER = "packet.handler.count"
const val PACKET_HANDLER_COUNTER_DESC = "包写入数量"
const val PACKET_HANDLER_SIZE_COUNTER = "packet.handler.size.count"
const val PACKET_HANDLER_SIZE_COUNTER_DESC = "包写入大小"
const val PACKET_LOSS_COUNTER = "packet.loss.count"
const val PACKET_LOSS_COUNTER_DESC = "丢包数量"
const val PACKET_QUEUE_COUNTER = "packet.queue.count"
const val PACKET_QUEUE_COUNTER_DESC = "包队列大小"
const val RECORDING_COUNTER = "recording.count"
const val RECORDING_COUNTER_DESC = "正在录制数量"