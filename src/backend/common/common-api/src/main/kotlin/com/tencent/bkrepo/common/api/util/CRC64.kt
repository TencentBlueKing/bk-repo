/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.tencent.bkrepo.common.api.util

import java.util.zip.Checksum

/**
 * https://github.com/tencentyun/cos-java-sdk-v5/blob/v5.6.246.3/src/main/java/com/qcloud/cos/utils/CRC64.java
 *
 * CRC-64 implementation with ability to combine checksums calculated over
 * different blocks of data. Standard ECMA-182,
 * http://www.ecma-international.org/publications/standards/Ecma-182.htm
 */
class CRC64 : Checksum {
    /* Current CRC value. */
    private var value: Long

    constructor() {
        this.value = 0
    }

    constructor(value: Long) {
        this.value = value
    }

    constructor(b: ByteArray, len: Int) {
        this.value = 0
        update(b, len)
    }

    val bytes: ByteArray
        /**
         * Get 8 byte representation of current CRC64 value.
         */
        get() {
            val b = ByteArray(8)
            for (i in 0..7) {
                b[7 - i] = (this.value ushr (i * 8)).toByte()
            }
            return b
        }

    /**
     * Get long representation of current CRC64 value.
     */
    override fun getValue(): Long {
        return this.value
    }

    /**
     * Update CRC64 with new byte block.
     */
    fun update(b: ByteArray, len: Int) {
        var currentLen = len
        var idx = 0
        this.value = value.inv()
        while (currentLen > 0) {
            this.value = table[(this.value xor b[idx].toLong()).toInt() and 0xff] xor (this.value ushr 8)
            idx++
            currentLen--
        }
        this.value = value.inv()
    }

    /**
     * Update CRC64 with new byte.
     */
    fun update(b: Byte) {
        this.value = value.inv()
        this.value = table[(this.value xor b.toLong()).toInt() and 0xff] xor (this.value ushr 8)
        this.value = value.inv()
    }

    override fun update(b: Int) {
        update((b and 0xFF).toByte())
    }

    override fun update(b: ByteArray, off: Int, len: Int) {
        var currentLen = len
        var i = off
        while (currentLen > 0) {
            update(b[i++])
            currentLen--
        }
    }

    override fun reset() {
        this.value = 0
    }

    fun unsignedStringValue(): String = java.lang.Long.toUnsignedString(value)

    companion object {
        private const val POLY = -0x3693a86a2878f0beL // ECMA-182

        /* CRC64 calculation table. */
        private val table = LongArray(256)

        init {
            for (n in 0..255) {
                var crc = n.toLong()
                for (k in 0..7) {
                    crc = if ((crc and 1L) == 1L) {
                        crc ushr 1 xor POLY
                    } else {
                        crc ushr 1
                    }
                }
                table[n] = crc
            }
        }

        /**
         * Construct new CRC64 instance from byte array.
         */
        fun fromBytes(b: ByteArray): CRC64 {
            var l: Long = 0
            for (i in 0..3) {
                l = l shl 8
                l = l xor (b[i].toLong() and 0xFFL)
            }
            return CRC64(l)
        }

        /*
         * dimension of GF(2) vectors (length of CRC)
         */
        private const val GF2_DIM = 64

        private fun gf2MatrixTimes(mat: LongArray, vec: Long): Long {
            var curVec = vec
            var sum: Long = 0
            var idx = 0
            while (curVec != 0L) {
                if ((curVec and 1L) == 1L) sum = sum xor mat[idx]
                curVec = curVec ushr 1
                idx++
            }
            return sum
        }

        private fun gf2MatrixSquare(square: LongArray, mat: LongArray) {
            for (n in 0 until GF2_DIM) {
                square[n] = gf2MatrixTimes(mat, mat[n])
            }
        }

        /*
         * Return the CRC-64 of two sequential blocks, where summ1 is the CRC-64 of
         * the first block, summ2 is the CRC-64 of the second block, and len2 is the
         * length of the second block.
         */
        fun combine(summ1: CRC64, summ2: CRC64, len2: Long): CRC64 {
            // degenerate case.
            var curLen2 = len2
            if (curLen2 == 0L) return CRC64(summ1.getValue())
            var row: Long
            val even = LongArray(GF2_DIM) // even-power-of-two zeros operator
            val odd = LongArray(GF2_DIM) // odd-power-of-two zeros operator

            // put operator for one zero bit in odd
            odd[0] = POLY // CRC-64 polynomial

            row = 1
            var n = 1
            while (n < GF2_DIM) {
                odd[n] = row
                row = row shl 1
                n++
            }

            // put operator for two zero bits in even
            gf2MatrixSquare(even, odd)

            // put operator for four zero bits in odd
            gf2MatrixSquare(odd, even)

            // apply len2 zeros to crc1 (first square will put the operator for one
            // zero byte, eight zero bits, in even)
            var crc1 = summ1.getValue()
            val crc2 = summ2.getValue()
            do {
                // apply zeros operator for this bit of len2
                gf2MatrixSquare(even, odd)
                if ((curLen2 and 1L) == 1L) crc1 = gf2MatrixTimes(even, crc1)
                curLen2 = curLen2 ushr 1

                // if no more bits set, then done
                if (curLen2 == 0L) break

                // another iteration of the loop with odd and even swapped
                gf2MatrixSquare(odd, even)
                if ((curLen2 and 1L) == 1L) crc1 = gf2MatrixTimes(odd, crc1)
                curLen2 = curLen2 ushr 1

                // if no more bits set, then done
            } while (curLen2 != 0L)

            // return combined crc.
            crc1 = crc1 xor crc2
            return CRC64(crc1)
        }

        /*
         * Return the CRC-64 of two sequential blocks, where summ1 is the CRC-64 of
         * the first block, summ2 is the CRC-64 of the second block, and len2 is the
         * length of the second block.
         */
        fun combine(crc1: Long, crc2: Long, len2: Long): Long {
            // degenerate case.
            var curCrc1 = crc1
            var curLen2 = len2
            if (curLen2 == 0L) return curCrc1
            var row: Long
            val even = LongArray(GF2_DIM) // even-power-of-two zeros operator
            val odd = LongArray(GF2_DIM) // odd-power-of-two zeros operator

            // put operator for one zero bit in odd
            odd[0] = POLY // CRC-64 polynomial

            row = 1
            var n = 1
            while (n < GF2_DIM) {
                odd[n] = row
                row = row shl 1
                n++
            }

            // put operator for two zero bits in even
            gf2MatrixSquare(even, odd)

            // put operator for four zero bits in odd
            gf2MatrixSquare(odd, even)

            // apply len2 zeros to crc1 (first square will put the operator for one
            // zero byte, eight zero bits, in even)
            do {
                // apply zeros operator for this bit of len2
                gf2MatrixSquare(even, odd)
                if ((curLen2 and 1L) == 1L) curCrc1 = gf2MatrixTimes(even, curCrc1)
                curLen2 = curLen2 ushr 1

                // if no more bits set, then done
                if (curLen2 == 0L) break

                // another iteration of the loop with odd and even swapped
                gf2MatrixSquare(odd, even)
                if ((curLen2 and 1L) == 1L) curCrc1 = gf2MatrixTimes(odd, curCrc1)
                curLen2 = curLen2 ushr 1

                // if no more bits set, then done
            } while (curLen2 != 0L)

            // return combined crc.
            curCrc1 = curCrc1 xor crc2
            return curCrc1
        }
    }
}
