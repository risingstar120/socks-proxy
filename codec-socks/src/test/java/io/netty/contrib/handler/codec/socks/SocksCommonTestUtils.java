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
import io.netty5.channel.embedded.EmbeddedChannel;

final class SocksCommonTestUtils {
    /**
     * A constructor to stop this class being constructed.
     */
    private SocksCommonTestUtils() {
        //NOOP
    }

    @SuppressWarnings("deprecation")
    public static void writeMessageIntoEmbedder(EmbeddedChannel embedder, SocksMessage msg) {
        Buffer buf = embedder.bufferAllocator().allocate(64);
        msg.encodeAsBuffer(buf);
        embedder.writeInbound(buf);
    }
}
