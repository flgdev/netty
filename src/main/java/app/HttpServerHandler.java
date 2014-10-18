package app;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class HttpServerHandler extends SimpleChannelInboundHandler<Object> {

	private String redirUrl;
	HttpServerData serverData;
	private final StringBuilder buf = new StringBuilder();

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg)
			throws InterruptedException {
		if (msg instanceof HttpRequest) {
			HttpRequest request = (HttpRequest) msg;
			buf.setLength(0);
			serverData = new HttpServerData();
			redirUrl = serverData.getPage(buf, request, ctx);
			request.getDecoderResult();
		}

		if (msg instanceof HttpContent) {
			if (msg instanceof LastHttpContent) {
				LastHttpContent trailer = (LastHttpContent) msg;
				FullHttpResponse response; 
				if(redirUrl.equals("")){
					response = new DefaultFullHttpResponse(
						HTTP_1_1, trailer.getDecoderResult().isSuccess() ? OK
								: BAD_REQUEST, Unpooled.copiedBuffer(
								buf.toString(), CharsetUtil.UTF_8));
				}else{
					response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
		            response.headers().set(LOCATION, redirUrl);
				}
				ctx.write(response);
				ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
						ChannelFutureListener.CLOSE);
				serverData.putLog(response,buf.length());
			}
		}
	}
}