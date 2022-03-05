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

import io.netty5.buffer.ByteBuf;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.ByteToMessageDecoder;

/**
 * Decodes {@link ByteBuf}s into {@link SocksInitResponse}.
 * Before returning SocksResponse decoder removes itself from pipeline.
 */
public class SocksInitResponseDecoder extends ByteToMessageDecoder {

    private enum State {
        CHECK_PROTOCOL_VERSION,
        READ_PREFERRED_AUTH_TYPE
    }
    private State state = State.CHECK_PROTOCOL_VERSION;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        switch (state) {
            case CHECK_PROTOCOL_VERSION: {
                if (byteBuf.readableBytes() < 1) {
                    return;
                }
                if (byteBuf.readByte() != SocksProtocolVersion.SOCKS5.byteValue()) {
                    ctx.fireChannelRead(SocksCommonUtils.UNKNOWN_SOCKS_RESPONSE);
                    break;
                }
                state = State.READ_PREFERRED_AUTH_TYPE;
            }
            case READ_PREFERRED_AUTH_TYPE: {
                if (byteBuf.readableBytes() < 1) {
                    return;
                }
                SocksAuthScheme authScheme = SocksAuthScheme.valueOf(byteBuf.readByte());
                ctx.fireChannelRead(new SocksInitResponse(authScheme));
                break;
            }
            default: {
                throw new Error();
            }
        }
        ctx.pipeline().remove(this);
    }
}
