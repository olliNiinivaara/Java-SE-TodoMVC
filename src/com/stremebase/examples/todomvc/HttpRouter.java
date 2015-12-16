/*
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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.router.RouteResult;
import io.netty.handler.codec.http.router.Router;
import io.netty.util.CharsetUtil;

import static com.stremebase.examples.todomvc.Todo.*;

@ChannelHandler.Sharable
public class HttpRouter extends SimpleChannelInboundHandler<HttpRequest>
{
  public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
  public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
  public static final int HTTP_CACHE_SECONDS = 3600;

  public static final SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
  public static final Calendar time = new GregorianCalendar();

  private final Router<Integer> router;

  public HttpRouter(Router<Integer> router)
  {
    this.router = router;
    dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE)); 
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, HttpRequest req)
  {
    if (HttpHeaders.is100ContinueExpected(req))
    {
      ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
      return;
    }

    HttpResponse res = createResponse(req, router);
    flushResponse(ctx, req, res);
  }

  private static HttpResponse createResponse(HttpRequest req, Router<Integer> router)
  {
    RouteResult<Integer> routeResult = router.route(req.getMethod(), req.getUri());

    Integer request = routeResult.target();

    String data = "";
    String mimeType = "";

    if (request==CSS)
    {
      data = Todo.getCss();
      mimeType="text/css";
    }
    else if (request==ICON)
    {
      mimeType="image/x-icon";
    }
    else if (request==GET)
    {
      data = Todo.get();
      mimeType="text/html";
    }
    else if (request==FILTER)
    {
      data = Todo.filter(routeResult.pathParams().get("filtertype"));
      mimeType="text/html";
    }
    else if (req.getMethod().equals(HttpMethod.POST))
    {
      HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), req);

      Attribute attribute;

      String item_text = null;
      InterfaceHttpData httpData = decoder.getBodyHttpData("item-text");
      if (httpData!=null)
      {
        attribute = (Attribute) httpData;
        try {item_text = attribute.getValue();} catch (IOException e) {e.printStackTrace();}
      }

      String item_id = null;
      httpData = decoder.getBodyHttpData("item-id");
      if (httpData!=null)
      {
        attribute = (Attribute) httpData;
        try {item_id = attribute.getValue();} catch (IOException e) {e.printStackTrace();}
      }

      if (request==POST)
      {
        if (item_id==null) data = Todo.create(item_text);
        else data = Todo.save(Long.valueOf(item_id), item_text);
      }
      else if (request==DELETE)
      {
        data = Todo.delete(Long.valueOf(item_id));
      }
      else if (request==DELETECOMPLETED)
      {
        data = Todo.clearCompleted();
      }
      else if (request==TOGGLESTATUS) data=Todo.toggleStatus(Long.valueOf(item_id));

      mimeType = "text/html";
      decoder.destroy();
    }

    FullHttpResponse res;

    if (request==NOTFOUND)
    {
      res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT);
      res.headers().add(HttpHeaders.Names.LOCATION, "/");
      return res;
    }

    if (request==ICON) res = new DefaultFullHttpResponse (
        HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
        Unpooled.copiedBuffer(Todo.favicon)
        );
    else res = new DefaultFullHttpResponse (
        HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
        Unpooled.copiedBuffer(data, CharsetUtil.UTF_8)
        );

    res.headers().set(HttpHeaders.Names.CONTENT_TYPE, mimeType);
    res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());
    if (request==CSS || request == ICON) setDateAndCacheHeaders(res);

    return res;
  }

  private static ChannelFuture flushResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res)
  {
    if (!HttpHeaders.isKeepAlive(req))
    {
      return ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
    else
    {
      res.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
      return ctx.writeAndFlush(res);
    }
  }

  private static void setDateAndCacheHeaders(HttpResponse response)
  {
    response.headers().set(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
    time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
    response.headers().set(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));
    response.headers().set(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
  }
}