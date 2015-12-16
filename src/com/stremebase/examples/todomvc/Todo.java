/*
 * You need to add stremebase_0_7_1.jar or later to build path
 * Get it from https://github.com/olliNiinivaara/Stremebase-source 
 *
 * Use maven generate-sources to generate views (and add generated sources to build path)
 * 
 * 
 * This file includes source code developed at
 * The Netty Project (http://netty.io/).
 * 
 * Copyright 2015 Olli Niinivaara
 *
 * Olli Niinivaara licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.stremebase.examples.todomvc;


import java.nio.file.Files;
import java.nio.file.Paths;
import com.stremebase.base.DB;
import com.stremebase.dal.Table;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.ThreadPerChannelEventLoop;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.handler.codec.http.BadClientSilencer;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.router.Router;


public class Todo
{
  static class HttpRouterServerInitializer extends ChannelInitializer<SocketChannel>
  {
    private final HttpRouter handler;
    private final BadClientSilencer badClientSilencer = new BadClientSilencer();

    public HttpRouterServerInitializer(Router<Integer> router)
    {
      handler = new HttpRouter(router);
    }

    @Override
    public void initChannel(SocketChannel ch)
    {
      ch.pipeline()
      .addLast(new HttpServerCodec())
      .addLast(new HttpObjectAggregator(1048576))
      .addLast(handler)
      .addLast(badClientSilencer);
    }
  }

  public static final int PORT = 8000;

  public static final int itemTableId = 0;

  public static final Integer NOTFOUND = 0;
  public static final Integer CSS = 1;
  public static final Integer ICON = 2;
  public static final Integer GET = 3;
  public static final Integer POST = 4;
  public static final Integer DELETE = 5;
  public static final Integer DELETECOMPLETED = 6;  
  public static final Integer TOGGLESTATUS = 7;
  public static final Integer FILTER = 8;

  public static String css;

  public static byte[] favicon;

  public static Data data;

  public static void main( String[] args ) throws Exception
  {
    css = new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir"),"Web","index.css")));
    favicon = Files.readAllBytes(Paths.get(System.getProperty("user.dir"),"Web","favicon.ico"));

    Table.setDefaultDb(new DB("user.dir"));
    data = new Data(itemTableId, "ITEMTABLE");

    @SuppressWarnings("unchecked")
    Router<Integer> router = new Router<Integer>()
    .GET("/",               GET)
    .GET("/filter/:filtertype", FILTER)

    .POST("/",              POST)
    .POST("/delete",        DELETE)
    .POST("/clearcompleted",        DELETECOMPLETED)
    .POST("/toggle-status", TOGGLESTATUS)

    .GET(":something/index.css", CSS)
    .GET("/index.css",      CSS)
    .GET("/favicon.ico",      ICON) 
    .notFound(              NOTFOUND);
    System.out.println(router);

    OioEventLoopGroup bossGroup   = new OioEventLoopGroup(1);
    SingleThreadEventLoop workerGroup = new ThreadPerChannelEventLoop(bossGroup);

    try
    {
      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
      .childOption(ChannelOption.TCP_NODELAY, java.lang.Boolean.TRUE)
      .childOption(ChannelOption.SO_KEEPALIVE, java.lang.Boolean.TRUE)
      .childOption(ChannelOption.SO_REUSEADDR, java.lang.Boolean.TRUE)
      .channel(OioServerSocketChannel.class)
      .childHandler(new HttpRouterServerInitializer(router));

      Channel ch = b.bind(PORT).sync().channel();
      System.out.println("Server started: http://127.0.0.1:" + PORT + '/');

      ch.closeFuture().sync();
    }
    finally
    {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
    }
  }

  public static String get()
  {
    return views.index.template(data).render().toString();
  }

  public static String filter(String filtertype)
  {
    data.filter(filtertype);
    return get();
  }

  public static String clearCompleted()
  {
    data.clearCompleted();
    return get();
  }

  public static String create(String text)
  {
    if (text!=null) data.createItem(text);
    return views.index.template(data).render().toString();
  }

  public static String save(long id, String text)
  {
    data.saveText(id, text);
    return views.index.template(data).render().toString();
  }

  public static String delete(long id)
  {
    data.deleteItem(id);
    return views.index.template(data).render().toString();
  }

  public static String toggleStatus(long id)
  {
    data.toggleStatus(id);
    return views.index.template(data).render().toString();
  }

  public static String getCss()
  {
    return css;
  }

  public String getNotFound()
  {
    return "";
  }
}
