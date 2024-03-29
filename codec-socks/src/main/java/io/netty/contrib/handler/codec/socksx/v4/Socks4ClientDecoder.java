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
package io.netty.contrib.handler.codec.socksx.v4;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.handler.codec.DecoderException;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.util.NetUtil;

/**
 * Decodes a single {@link Socks4CommandResponse} from the inbound {@link Buffer}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove this decoder later.  On failed decode, this decoder will discard the
 * received data, so that other handler closes the connection later.
 */
public class Socks4ClientDecoder extends ByteToMessageDecoder {

    private enum State {
        START,
        SUCCESS,
        FAILURE
    }

    private State state = State.START;

    public Socks4ClientDecoder() {
        setSingleDecode(true);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
        try {
            switch (state) {
            case START: {
                if (in.readableBytes() < 8) {
                    // Not enough data to read.
                    return;
                }
                final int version = in.readUnsignedByte();
                if (version != 0) {
                    throw new DecoderException("unsupported reply version: " + version + " (expected: 0)");
                }

                final Socks4CommandStatus status = Socks4CommandStatus.valueOf(in.readByte());
                final int dstPort = in.readUnsignedShort();
                final String dstAddr = NetUtil.intToIpAddress(in.readInt());

                ctx.fireChannelRead(new DefaultSocks4CommandResponse(status, dstAddr, dstPort));
                state = State.SUCCESS;
                // fall-through
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

        Socks4CommandResponse m = new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED);
        m.setDecoderResult(DecoderResult.failure(cause));
        ctx.fireChannelRead(m);

        state = State.FAILURE;
    }
}
