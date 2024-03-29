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
package io.netty.contrib.handler.proxy;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty.contrib.handler.proxy.HttpProxyHandler.HttpProxyConnectException;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.DefaultHttpResponse;
import io.netty5.handler.codec.http.EmptyLastHttpContent;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpClientCodec;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.HttpResponseEncoder;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpProxyHandlerTest {

    @Test
    public void testHostname() throws Exception {
        InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName("localhost"), 8080);
        testInitialMessage(
                socketAddress,
                "localhost:8080",
                "localhost:8080",
                null,
                true);
    }

    @Test
    public void testHostnameUnresolved() throws Exception {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("localhost", 8080);
        testInitialMessage(
                socketAddress,
                "localhost:8080",
                "localhost:8080",
                null,
                true);
    }

    @Test
    public void testHostHeaderWithHttpDefaultPort() throws Exception {
        InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName("localhost"), 80);
        testInitialMessage(socketAddress,
                "localhost:80",
                "localhost:80", null,
                false);
    }

    @Test
    public void testHostHeaderWithHttpDefaultPortIgnored() throws Exception {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("localhost", 80);
        testInitialMessage(
                socketAddress,
                "localhost:80",
                "localhost",
                null,
                true);
    }

    @Test
    public void testHostHeaderWithHttpsDefaultPort() throws Exception {
        InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName("localhost"), 443);
        testInitialMessage(
                socketAddress,
                "localhost:443",
                "localhost:443",
                null,
                false);
    }

    @Test
    public void testHostHeaderWithHttpsDefaultPortIgnored() throws Exception {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("localhost", 443);
        testInitialMessage(
                socketAddress,
                "localhost:443",
                "localhost",
                null,
                true);
    }

    @Test
    public void testIpv6() throws Exception {
        InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName("::1"), 8080);
        testInitialMessage(
                socketAddress,
                "[::1]:8080",
                "[::1]:8080",
                null,
                true);
    }

    @Test
    public void testIpv6Unresolved() throws Exception {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("::1", 8080);
        testInitialMessage(
                socketAddress,
                "[::1]:8080",
                "[::1]:8080",
                null,
                true);
    }

    @Test
    public void testIpv4() throws Exception {
        InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 8080);
        testInitialMessage(socketAddress,
                "10.0.0.1:8080",
                "10.0.0.1:8080",
                null,
                true);
    }

    @Test
    public void testIpv4Unresolved() throws Exception {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("10.0.0.1", 8080);
        testInitialMessage(
                socketAddress,
                "10.0.0.1:8080",
                "10.0.0.1:8080",
                null,
                true);
    }

    @Test
    public void testCustomHeaders() throws Exception {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("10.0.0.1", 8080);
        testInitialMessage(
                socketAddress,
                "10.0.0.1:8080",
                "10.0.0.1:8080",
                HttpHeaders.newHeaders()
                        .add("CUSTOM_HEADER", "CUSTOM_VALUE1")
                        .add("CUSTOM_HEADER", "CUSTOM_VALUE2"),
                true);
    }

    @Test
    public void testExceptionDuringConnect() throws Exception {
        EventLoopGroup group = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
            final LocalAddress addr = new LocalAddress("a");
            final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
            Future<Channel> sf =
                new ServerBootstrap().channel(LocalServerChannel.class).group(group).childHandler(
                    new ChannelInitializer<Channel>() {

                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addFirst(new HttpResponseEncoder());
                            ch.pipeline().addFirst(new ChannelHandler() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.BAD_GATEWAY,
                                            preferredAllocator().allocate(0));
                                    response.headers().add("name", "value");
                                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, "0");
                                    ctx.writeAndFlush(response);
                                }
                            });
                        }
                    }).bind(addr);
            serverChannel = sf.asStage().get();
            Future<Channel> cf = new Bootstrap().channel(LocalChannel.class).group(group).handler(
                new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addFirst(new HttpProxyHandler(addr));
                        ch.pipeline().addLast(new ChannelHandler() {
                            @Override
                            public void channelExceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) {
                                exception.set(cause);
                            }
                        });
                    }
                }).connect(new InetSocketAddress("localhost", 1234));
            clientChannel = cf.asStage().get();
            clientChannel.close().asStage().sync();

            assertTrue(exception.get() instanceof HttpProxyConnectException);
            HttpProxyConnectException actual = (HttpProxyConnectException) exception.get();
            assertNotNull(actual.headers());
            assertEquals("value", actual.headers().get("name"));
        } finally {
            if (clientChannel != null) {
                clientChannel.close();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (group != null) {
                group.shutdownGracefully();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void testInitialMessage(InetSocketAddress socketAddress,
                                           String expectedUrl,
                                           String expectedHostHeader,
                                           HttpHeaders headers,
                                           boolean ignoreDefaultPortsInConnectHostHeader) throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress(NetUtil.LOCALHOST, 8080);

        Future<Void> future = mock(Future.class);

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.connect(same(proxyAddress), isNull())).thenReturn(future);
        when(ctx.bufferAllocator()).thenReturn(preferredAllocator());

        HttpProxyHandler handler = new HttpProxyHandler(
                new InetSocketAddress(NetUtil.LOCALHOST, 8080),
                headers,
                ignoreDefaultPortsInConnectHostHeader);
        handler.connect(ctx, socketAddress, null);

        try (FullHttpRequest request = (FullHttpRequest) handler.newInitialMessage(ctx)) {
            assertEquals(HttpVersion.HTTP_1_1, request.protocolVersion());
            assertEquals(expectedUrl, request.uri());
            HttpHeaders actualHeaders = request.headers();
            assertEquals(expectedHostHeader, actualHeaders.get(HttpHeaderNames.HOST));

            if (headers != null) {
                // The actual request header is a strict superset of the custom header
                for (CharSequence name : headers.names()) {
                    assertIterableEquals(headers.values(name), actualHeaders.values(name));
                }
            }
        }
        verify(ctx).connect(proxyAddress, null);
    }

    @Test
    public void testHttpClientCodecIsInvisible() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpProxyHandler(
                new InetSocketAddress(NetUtil.LOCALHOST, 8080))) {
            @Override
            public boolean isActive() {
                // We want to simulate that the Channel did not become active yet.
                return false;
            }
        };
        assertNotNull(channel.pipeline().get(HttpProxyHandler.class));
        assertNull(channel.pipeline().get(HttpClientCodec.class));
    }

    @Test
    void testAllMessagesAreReleased() {
        HttpProxyHandler proxyHandler = new HttpProxyHandler(new InetSocketAddress(NetUtil.LOCALHOST, 8080));
        ChannelHandlerAdapter testHandler =
                new ChannelHandlerAdapter() {

                    @Override
                    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        // ProxyConnectException is expected as there is no real proxy setup
                        if (!(cause instanceof ProxyConnectException)) {
                            ctx.fireChannelExceptionCaught(cause);
                        }
                    }
                };
        EmbeddedChannel channel = new EmbeddedChannel(proxyHandler, testHandler) {

            @Override
            public boolean isActive() {
                // Initial request to proxy cannot be sent as there is no real proxy setup
                return false;
            }
        };

        DefaultHttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        EmptyLastHttpContent emptyLastHttpContent = new EmptyLastHttpContent(preferredAllocator());
        channel.writeInbound(httpResponse, emptyLastHttpContent);

        assertFalse(emptyLastHttpContent.isAccessible());
    }
}
