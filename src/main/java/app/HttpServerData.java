package app;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServerData {
	private static AtomicInteger connections = new AtomicInteger(0);
	private static Map<String, IpStat> statByIp = new ConcurrentHashMap<String, IpStat>();
	private static Map<String, Integer> statByUrl = new ConcurrentHashMap<String, Integer>();
	private static Vector<LogItem> log = new Vector<LogItem>();
	private int logId;
	private long nano;
	
	class IpStat {
		private int count;
		private long time;

		public IpStat(int count, long time) {
			this.count = count;
			this.time = time;
		}

		public int getCount() {
			return count;
		}

		public long getTime() {
			return time;
		}
	}

	class LogItem {
		private String remIP;
		private String URI;
		private long time;
		private int recBytes;
		private int sentBytes;
		private int speed;

		LogItem(String remIP, String URI, long time, int recBytes) {
			this.remIP = remIP;
			this.URI = URI;
			this.time = time;
			this.recBytes = recBytes;
		}

		public String toString() {
			return "<tr><td>" + remIP + "<td>" + URI + "<td>"
					+ millToDate(time) + "<td>" + sentBytes + "<td>" + recBytes
					+ "<td>" + speed;
		}

		public void setSent(int sentBytes) {
			this.sentBytes = sentBytes;
			this.speed = (int) (sentBytes / ((System.nanoTime() - nano) / 1000000000.0));
		}
	}

	public String getPage(StringBuilder buf, HttpRequest request,
			ChannelHandlerContext ctx) throws InterruptedException {
		connections.addAndGet(1);
		nano = System.nanoTime();
		String URI = request.getUri();
		long reqTime = System.currentTimeMillis();
		String uri = cutstr(URI, "?", true);
		String rm = "" + ctx.channel().remoteAddress();
		String remIP = rm.substring(1, rm.indexOf(':'));
		int recBytes = cutstr(request.toString(),
				System.getProperty("line.separator"), false).length();

		int count = 0;
		if (statByIp.containsKey(remIP)) {
			count = statByIp.get(remIP).getCount();
		}
		statByIp.put(remIP, new IpStat(count + 1, reqTime));

		synchronized (log) {
			logId = log.size();
			log.add(new LogItem(remIP, uri, reqTime, recBytes));
		}

		String redir = "";
		switch (uri) {
		case "/hello":
			Thread.sleep(10000);
			buf.append("Hello World\r\n");
			break;

		case "/status":
			buf.append(getReport());
			break;

		case "/redirect":
			QueryStringDecoder qsd = new QueryStringDecoder(URI);
			redir = qsd.parameters().get("url").get(0);
			break;
		}

		if (redir.length() > 0) {
			int cnt = 0;
			if (statByUrl.containsKey(redir)) {
				cnt = statByUrl.get(redir);
			}
			statByUrl.put(redir, cnt + 1);
		}

		return redir;
	}

	public void putLog(FullHttpResponse response, int contentSize) {
		int sent = cutstr(response.toString(),
				System.getProperty("line.separator"), false).length();
		log.get(logId).setSent(sent + contentSize);
		connections.addAndGet(-1);
	}

	private static String getReport() {
		String s = "<table><tr><td>\r\n<table border=1>\r\n"
				+ "<tr><td>Total requests <td>"
				+ log.size()
				+ "\r\n<tr><td>Unique requests <td>"
				+ statByIp.size()
				+ "\r\n<tr><td>Connections <td>"
				+ connections
				+ "\r\n"
				+ "	</table>\r\n<br>\r\n<table border=1>\r\n<tr><td>URL<td>Count\r\n";

		for (Entry<String, Integer> entry : statByUrl.entrySet()) {
			s += "<tr><td>" + entry.getKey() + "<td>" + entry.getValue()
					+ "\r\n";
		}

		s += "</table>\r\n<br>\r\n<table border=1>\r\n<tr><td>IP<td>Requests<td>Last request\r\n";

		for (Entry<String, IpStat> entry : statByIp.entrySet()) {
			s += "<tr><td>" + entry.getKey() + "<td>"
					+ entry.getValue().getCount() + "<td>"
					+ millToDate(entry.getValue().getTime()) + "\r\n";
		}

		s += "</table>\r\n<td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<td>\r\n<table border=1>\r\n<tr><td>IP<td>URI<td>Timestamp<td>Sent bytes<td>Received bytes<td>Speed (bytes/sec)\r\n";

		int lim = Math.max(log.size()-17, -1);
		for (int j=log.size()-1;j>lim;j--){
			s += log.get(j).toString();
		}

		s += "	</table>\r\n</table>";
		return s;
	}

	private static String cutstr(String s, String delim, boolean before) {
		int m = s.indexOf(delim);
		if (m >= 0) {
			return before ? s.substring(0, m) : s.substring(m + delim.length());
		}
		return s;
	}

	private static String millToDate(long millis) {
		return new SimpleDateFormat("MM/dd/yy HH:mm:ss").format(millis);
	}
}