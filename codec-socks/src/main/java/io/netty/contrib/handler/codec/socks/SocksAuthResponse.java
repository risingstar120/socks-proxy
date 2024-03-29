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

import static java.util.Objects.requireNonNull;

/**
 * An socks auth response.
 *
 * @see SocksAuthRequest
 * @see SocksAuthResponseDecoder
 */
public final class SocksAuthResponse extends SocksResponse {
    private static final SocksSubnegotiationVersion SUBNEGOTIATION_VERSION = SocksSubnegotiationVersion.AUTH_PASSWORD;
    private final SocksAuthStatus authStatus;

    public SocksAuthResponse(SocksAuthStatus authStatus) {
        super(SocksResponseType.AUTH);
        requireNonNull(authStatus, "authStatus");
        this.authStatus = authStatus;
    }

    /**
     * Returns the {@link SocksAuthStatus} of this {@link SocksAuthResponse}
     *
     * @return The {@link SocksAuthStatus} of this {@link SocksAuthResponse}
     */
    public SocksAuthStatus authStatus() {
        return authStatus;
    }

    @Override
    public void encodeAsBuffer(Buffer buffer) {
        buffer.writeByte(SUBNEGOTIATION_VERSION.byteValue());
        buffer.writeByte(authStatus.byteValue());
    }
}
