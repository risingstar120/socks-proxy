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
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.EncoderException;
import io.netty5.handler.codec.MessageToByteEncoder;
import java.nio.charset.StandardCharsets;
import io.netty5.util.internal.StringUtil;

import java.util.List;
import java.util.RandomAccess;

import static java.util.Objects.requireNonNull;

/**
 * Encodes a client-side {@link Socks5Message} into a {@link Buffer}.
 */
public class Socks5ClientEncoder extends MessageToByteEncoder<Socks5Message> {

    public static final Socks5ClientEncoder DEFAULT = new Socks5ClientEncoder();

    private final Socks5AddressEncoder addressEncoder;

    /**
     * Creates a new instance with the default {@link Socks5AddressEncoder}.
     */
    protected Socks5ClientEncoder() {
        this(Socks5AddressEncoder.DEFAULT);
    }

    @Override
    protected Buffer allocateBuffer(ChannelHandlerContext ctx, Socks5Message msg) {
        return ctx.bufferAllocator().allocate(256);
    }

    /**
     * Creates a new instance with the specified {@link Socks5AddressEncoder}.
     */
    public Socks5ClientEncoder(Socks5AddressEncoder addressEncoder) {
        requireNonNull(addressEncoder, "addressEncoder");

        this.addressEncoder = addressEncoder;
    }

    /**
     * Returns the {@link Socks5AddressEncoder} of this encoder.
     */
    protected final Socks5AddressEncoder addressEncoder() {
        return addressEncoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks5Message msg, Buffer out) throws Exception {
        if (msg instanceof Socks5InitialRequest) {
            encodeAuthMethodRequest((Socks5InitialRequest) msg, out);
        } else if (msg instanceof Socks5PasswordAuthRequest) {
            encodePasswordAuthRequest((Socks5PasswordAuthRequest) msg, out);
        } else if (msg instanceof Socks5CommandRequest) {
            encodeCommandRequest((Socks5CommandRequest) msg, out);
        } else {
            throw new EncoderException("unsupported message type: " + StringUtil.simpleClassName(msg));
        }
    }

    private static void encodeAuthMethodRequest(Socks5InitialRequest msg, Buffer out) {
        out.writeByte(msg.version().byteValue());

        final List<Socks5AuthMethod> authMethods = msg.authMethods();
        final int numAuthMethods = authMethods.size();
        out.writeByte((byte) numAuthMethods);

        if (authMethods instanceof RandomAccess) {
            for (int i = 0; i < numAuthMethods; i ++) {
                out.writeByte(authMethods.get(i).byteValue());
            }
        } else {
            for (Socks5AuthMethod a: authMethods) {
                out.writeByte(a.byteValue());
            }
        }
    }

    private static void encodePasswordAuthRequest(Socks5PasswordAuthRequest msg, Buffer out) {
        out.writeByte((byte) 0x01);

        final String username = msg.username();
        out.writeByte((byte) username.length());
        out.writeCharSequence(username, StandardCharsets.US_ASCII);

        final String password = msg.password();
        out.writeByte((byte) password.length());
        out.writeCharSequence(password, StandardCharsets.US_ASCII);
    }

    private void encodeCommandRequest(Socks5CommandRequest msg, Buffer out) throws Exception {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.type().byteValue());
        out.writeByte((byte) 0x00);

        final Socks5AddressType dstAddrType = msg.dstAddrType();
        out.writeByte(dstAddrType.byteValue());
        addressEncoder.encodeAddress(dstAddrType, msg.dstAddr(), out);
        out.writeShort((short) msg.dstPort());
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
