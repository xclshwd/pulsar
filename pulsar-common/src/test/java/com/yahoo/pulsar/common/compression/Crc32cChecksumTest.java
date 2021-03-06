/**
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.pulsar.common.compression;

import static com.scurrilous.circe.params.CrcParameters.CRC32C;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.scurrilous.circe.IncrementalIntHash;
import com.scurrilous.circe.crc.StandardCrcProvider;
import com.yahoo.pulsar.checksum.utils.Crc32cChecksum;
import com.yahoo.pulsar.checksum.utils.Crc32cSse42Provider;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

public class Crc32cChecksumTest {

    private static final byte[] inputBytes = "data".getBytes();
    private static final int expectedChecksum = 0xaed87dd1;

    private static final IncrementalIntHash SOFTWARE_CRC32C_HASH = new StandardCrcProvider().getIncrementalInt(CRC32C);
    private static final IncrementalIntHash HARDWARE_CRC32C_HASH;

    static {
        IncrementalIntHash hardwareCRC32C = null;
        try {
            hardwareCRC32C = new Crc32cSse42Provider().getIncrementalInt(CRC32C);
        } catch (Throwable t) {
            // Native CRC32C Not supported, skip tests
            hardwareCRC32C = null;
        }

        HARDWARE_CRC32C_HASH = hardwareCRC32C;
    }

    @Test
    public void testCrc32c() {
        ByteBuf payload = Unpooled.wrappedBuffer(inputBytes);
        int checksum = Crc32cChecksum.computeChecksum(payload);
        payload.release();
        assertEquals(expectedChecksum, checksum);
    }

    @Test
    public void testCrc32cHardware() {
        if (HARDWARE_CRC32C_HASH == null) {
            return;
        }

        ByteBuf payload = Unpooled.wrappedBuffer(inputBytes);

        // compute checksum using sse4.2 hw instruction
        int hw = HARDWARE_CRC32C_HASH.calculate(payload.array(), payload.arrayOffset() + payload.readerIndex(),
                payload.readableBytes());
        assertEquals(hw, expectedChecksum);
    }

    @Test
    public void testCrc32cSoftware() {
        ByteBuf payload = Unpooled.wrappedBuffer(inputBytes);

        // compute checksum using sw algo
        int sw = SOFTWARE_CRC32C_HASH.calculate(payload.array(), payload.arrayOffset() + payload.readerIndex(),
                payload.readableBytes());
        assertEquals(sw, expectedChecksum);
    }

    @Test
    public void testCrc32cDirectMemoryHardware() {
        if (HARDWARE_CRC32C_HASH == null) {
            return;
        }

        ByteBuf payload = ByteBufAllocator.DEFAULT.directBuffer(inputBytes.length);
        payload.writeBytes(inputBytes);

        // read directly from memory address
        int checksum = HARDWARE_CRC32C_HASH.calculate(payload.memoryAddress(), payload.readableBytes());

        payload.release();
        assertEquals(checksum, expectedChecksum);
    }
}
