/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.contrib.handler.codec.socksx.v5;

import io.netty5.buffer.Buffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultSocks5CommandResponseTest {
    @Test
    public void testConstructorParamsAreNotNull() {
        assertThrows(NullPointerException.class,
                () -> new DefaultSocks5CommandResponse(null, Socks5AddressType.DOMAIN));
        assertThrows(NullPointerException.class,
                () -> new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, null));
    }

    /**
     * Verifies content of the response when domain is not specified.
     */
    @Test
    public void testEmptyDomain() {
        Socks5CommandResponse socks5CmdResponse = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.DOMAIN);
        assertNull(socks5CmdResponse.bndAddr());
        assertEquals(0, socks5CmdResponse.bndPort());

        try (Buffer buffer = Socks5CommonTestUtils.encodeServer(socks5CmdResponse)) {
            byte[] expected = {
                    0x05, // version
                    0x00, // success reply
                    0x00, // reserved
                    0x03, // address type domain
                    0x00, // length of domain
                    0x00, // port value
                    0x00
            };
            assertBufferEquals(expected, buffer);
        }
    }

    /**
     * Verifies content of the response when IPv4 address is specified.
     */
    @Test
    public void testIPv4Host() {
        Socks5CommandResponse socks5CmdResponse = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "127.0.0.1", 80);
        assertEquals("127.0.0.1", socks5CmdResponse.bndAddr());
        assertEquals(80, socks5CmdResponse.bndPort());

        try (Buffer buffer = Socks5CommonTestUtils.encodeServer(socks5CmdResponse)) {
            byte[] expected = {
                    0x05, // version
                    0x00, // success reply
                    0x00, // reserved
                    0x01, // address type IPv4
                    0x7F, // address 127.0.0.1
                    0x00,
                    0x00,
                    0x01,
                    0x00, // port
                    0x50
            };
            assertBufferEquals(expected, buffer);
        }
    }

    /**
     * Verifies that empty domain is allowed Response.
     */
    @Test
    public void testEmptyBoundAddress() {
        Socks5CommandResponse socks5CmdResponse = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.DOMAIN, "", 80);
        assertEquals("", socks5CmdResponse.bndAddr());
        assertEquals(80, socks5CmdResponse.bndPort());

        try (Buffer buffer = Socks5CommonTestUtils.encodeServer(socks5CmdResponse)) {
            byte[] expected = {
                    0x05, // version
                    0x00, // success reply
                    0x00, // reserved
                    0x03, // address type domain
                    0x00, // domain length
                    0x00, // port
                    0x50
            };
            assertBufferEquals(expected, buffer);
        }
    }

    /**
     * Verifies that Response cannot be constructed with invalid IP.
     */
    @Test
    public void testInvalidBoundAddress() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "127.0.0", 1000));
    }

    private static void assertBufferEquals(byte[] expected, Buffer actual) {
        byte[] actualBytes = new byte[actual.readableBytes()];
        actual.readBytes(actualBytes, 0, actualBytes.length);
        assertEquals(expected.length, actualBytes.length, "Generated response has incorrect length");
        assertArrayEquals(expected, actualBytes, "Generated response differs from expected");
    }

    @Test
    public void testValidPortRange() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "127.0.0", 0));
        assertThrows(IllegalArgumentException.class, () -> new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "127.0.0", 65536));
    }
}
