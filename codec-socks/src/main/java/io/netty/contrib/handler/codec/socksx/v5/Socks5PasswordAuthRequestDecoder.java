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
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.handler.codec.DecoderException;
import io.netty5.handler.codec.DecoderResult;
import java.nio.charset.StandardCharsets;

/**
 * Decodes a single {@link Socks5PasswordAuthRequest} from the inbound {@link Buffer}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */
public class Socks5PasswordAuthRequestDecoder extends ByteToMessageDecoder {

    private enum State {
        INIT,
        SUCCESS,
        FAILURE
    }

    private State state = State.INIT;

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
        try {
            switch (state) {
            case INIT: {
                if (in.readableBytes() < 3) {
                    return;
                }
                final int startOffset = in.readerOffset();
                final byte version = in.getByte(startOffset);
                if (version != 1) {
                    throw new DecoderException("unsupported subnegotiation version: " + version + " (expected: 1)");
                }

                final int usernameLength = in.getUnsignedByte(startOffset + 1);
                final int passwordLength = in.getUnsignedByte(startOffset + 2 + usernameLength);
                final int totalLength = usernameLength + passwordLength + 3;
                if (in.readableBytes() < totalLength) {
                    return;
                }
                in.skipReadableBytes(2);
                String username = in.readCharSequence(usernameLength, StandardCharsets.US_ASCII).toString();
                in.skipReadableBytes(1);
                String password = in.readCharSequence(passwordLength, StandardCharsets.US_ASCII).toString();
                ctx.fireChannelRead(new DefaultSocks5PasswordAuthRequest(username, password));

                state = State.SUCCESS;
            }
            case SUCCESS: {
                int readableBytes = actualReadableBytes();
                if (readableBytes > 0) {
                    ctx.fireChannelRead(in.readSplit(readableBytes));
                }
                break;
            }
            case FAILURE: {
                in.skipReadableBytes(actualReadableBytes());
                break;
            }
            }
        } catch (Exception e) {
            fail(ctx, e);
        }
    }

    private void fail(ChannelHandlerContext ctx, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        state = State.FAILURE;

        Socks5Message m = new DefaultSocks5PasswordAuthRequest("", "");
        m.setDecoderResult(DecoderResult.failure(cause));
        ctx.fireChannelRead(m);
    }
}
