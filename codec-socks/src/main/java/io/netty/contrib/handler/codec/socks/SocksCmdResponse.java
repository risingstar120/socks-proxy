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
package io.netty.contrib.handler.codec.socks;

import io.netty5.buffer.Buffer;
import java.nio.charset.StandardCharsets;
import io.netty5.util.NetUtil;

import java.net.IDN;

import static java.util.Objects.requireNonNull;

/**
 * A socks cmd response.
 *
 * @see SocksCmdRequest
 * @see SocksCmdResponseDecoder
 */
public final class SocksCmdResponse extends SocksResponse {
    private final SocksCmdStatus cmdStatus;

    private final SocksAddressType addressType;
    private final String host;
    private final int port;

    // All arrays are initialized on construction time to 0/false/null remove array Initialization
    private static final byte[] DOMAIN_ZEROED = {0x00};
    private static final byte[] IPv4_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00};
    private static final byte[] IPv6_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00};

    public SocksCmdResponse(SocksCmdStatus cmdStatus, SocksAddressType addressType) {
        this(cmdStatus, addressType, null, 0);
    }

    /**
     * Constructs new response and includes provided host and port as part of it.
     *
     * @param cmdStatus status of the response
     * @param addressType type of host parameter
     * @param host host (BND.ADDR field) is address that server used when connecting to the target host.
     *             When null a value of 4/8 0x00 octets will be used for IPv4/IPv6 and a single 0x00 byte will be
     *             used for domain addressType. Value is converted to ASCII using {@link IDN#toASCII(String)}.
     * @param port port (BND.PORT field) that the server assigned to connect to the target host
     * @throws NullPointerException in case cmdStatus or addressType are missing
     * @throws IllegalArgumentException in case host or port cannot be validated
     * @see IDN#toASCII(String)
     */
    public SocksCmdResponse(SocksCmdStatus cmdStatus, SocksAddressType addressType, String host, int port) {
        super(SocksResponseType.CMD);
        requireNonNull(cmdStatus, "cmdStatus");
        requireNonNull(addressType, "addressType");
        if (host != null) {
            switch (addressType) {
                case IPv4:
                    if (!NetUtil.isValidIpV4Address(host)) {
                        throw new IllegalArgumentException(host + " is not a valid IPv4 address");
                    }
                    break;
                case DOMAIN:
                    String asciiHost = IDN.toASCII(host);
                    if (asciiHost.length() > 255) {
                        throw new IllegalArgumentException(host + " IDN: " + asciiHost + " exceeds 255 char limit");
                    }
                    host = asciiHost;
                    break;
                case IPv6:
                    if (!NetUtil.isValidIpV6Address(host)) {
                        throw new IllegalArgumentException(host + " is not a valid IPv6 address");
                    }
                    break;
                case UNKNOWN:
                    break;
            }
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException(port + " is not in bounds 0 <= x <= 65535");
        }
        this.cmdStatus = cmdStatus;
        this.addressType = addressType;
        this.host = host;
        this.port = port;
    }

    /**
     * Returns the {@link SocksCmdStatus} of this {@link SocksCmdResponse}
     *
     * @return The {@link SocksCmdStatus} of this {@link SocksCmdResponse}
     */
    public SocksCmdStatus cmdStatus() {
        return cmdStatus;
    }

    /**
     * Returns the {@link SocksAddressType} of this {@link SocksCmdResponse}
     *
     * @return The {@link SocksAddressType} of this {@link SocksCmdResponse}
     */
    public SocksAddressType addressType() {
        return addressType;
    }

    /**
     * Returns host that is used as a parameter in {@link SocksCmdType}.
     * Host (BND.ADDR field in response) is address that server used when connecting to the target host.
     * This is typically different from address which client uses to connect to the SOCKS server.
     *
     * @return host that is used as a parameter in {@link SocksCmdType}
     *         or null when there was no host specified during response construction
     */
    public String host() {
        return host != null && addressType == SocksAddressType.DOMAIN ? IDN.toUnicode(host) : host;
    }

    /**
     * Returns port that is used as a parameter in {@link SocksCmdType}.
     * Port (BND.PORT field in response) is port that the server assigned to connect to the target host.
     *
     * @return port that is used as a parameter in {@link SocksCmdType}
     */
    public int port() {
        return port;
    }

    @Override
    public void encodeAsBuffer(Buffer buffer) {
        buffer.writeByte(protocolVersion().byteValue());
        buffer.writeByte(cmdStatus.byteValue());
        buffer.writeByte((byte) 0x00);
        buffer.writeByte(addressType.byteValue());
        switch (addressType) {
            case IPv4: {
                byte[] hostContent = host == null ?
                        IPv4_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(host);
                buffer.writeBytes(hostContent);
                buffer.writeShort((short) port);
                break;
            }
            case DOMAIN: {
                if (host != null) {
                    buffer.writeByte((byte) host.length());
                    buffer.writeCharSequence(host, StandardCharsets.US_ASCII);
                } else {
                    buffer.writeByte((byte) DOMAIN_ZEROED.length);
                    buffer.writeBytes(DOMAIN_ZEROED);
                }
                buffer.writeShort((short) port);
                break;
            }
            case IPv6: {
                byte[] hostContent = host == null
                        ? IPv6_HOSTNAME_ZEROED : NetUtil.createByteArrayFromIpAddressString(host);
                buffer.writeBytes(hostContent);
                buffer.writeShort((short) port);
                break;
            }
        }
    }
}
