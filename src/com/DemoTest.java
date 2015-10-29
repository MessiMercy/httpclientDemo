package com;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class DemoTest {
	static final CloseableHttpClient client = HttpClients.createDefault();
	static CookieStore cookieStore = new BasicCookieStore();
	// 默认无代理，正常联网
	// static final CloseableHttpClient client = getInstanceClient();
	// client大致表示一个浏览器。static是必须的，保证能处处调用。否则无法保持cookie
	static RequestConfig config = RequestConfig.custom().setConnectTimeout(6000).setSocketTimeout(6000)
			.setCookieSpec(CookieSpecs.IGNORE_COOKIES).build(); // 设置超时及cookie策略

	/**
	 * initialize a instance of the httpClient depending on your own request
	 */
	private static CloseableHttpClient getInstanceClient() {
		CloseableHttpClient httpClient;
		StandardHttpRequestRetryHandler standardHandler = new StandardHttpRequestRetryHandler(5, true);
		HttpRequestRetryHandler handler = new HttpRequestRetryHandler() {

			@Override
			public boolean retryRequest(IOException arg0, int retryTimes, HttpContext arg2) {
				if (arg0 instanceof UnknownHostException || arg0 instanceof ConnectTimeoutException
						|| !(arg0 instanceof SSLException) || arg0 instanceof NoHttpResponseException) {
					return true;
				}
				if (retryTimes > 5) {
					return false;
				}
				HttpClientContext clientContext = HttpClientContext.adapt(arg2);
				HttpRequest request = clientContext.getRequest();
				boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
				if (idempotent) {
					// 如果请求被认为是幂等的，那么就重试。即重复执行不影响程序其他效果的
					return true;
				}
				retryTimes++;
				return false;
			}
		};
		HttpHost proxy = new HttpHost("127.0.0.1", 80);// 设置代理ip
		DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
		httpClient = HttpClients.custom().setRoutePlanner(routePlanner).setRetryHandler(standardHandler)
				.setConnectionTimeToLive(1, TimeUnit.DAYS).setDefaultCookieStore(cookieStore).build();
		return httpClient;
	}

	/**
	 * used to get the html code from the url
	 */
	public static String getDemo(String url) {
		url = "http://www.d.cn/";
		HttpGet get = new HttpGet(url);
		get.setConfig(config);
		HttpResponse response = null;
		String html = null;
		try {
			response = client.execute(get);
			response.getFirstHeader("key");// 得到第一个名字为key的header
			response.getHeaders("key");// 得到名字为key的所有header，返回一个数组
			response.getLastHeader("key");
			System.out.println("--------------------------------------");
			int statusCode = response.getStatusLine().getStatusCode();// 连接代码
			Header[] headers = response.getAllHeaders();
			// 用于得到返回的文件头
			for (Header header : headers) {
				System.out.println(header);
			}
			html = new String(EntityUtils.toString(response.getEntity()).getBytes("iso8859-1"), "gb2312");
			// 在后面参数输入网站的编码，一般为utf-8
			// 返回的html代码,避免发生编码错误
			// System.out.println(html);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return html;
	}

	/**
	 * used to parse html code
	 */
	public static void parseHtml(String html) {
		Document doc = Jsoup.parse(html);
		// doc = Jsoup.connect("url").get();
		// File input = new File("com.ea.game.pvz2_row.html");
		// doc = Jsoup.parse(input, "UTF-8", "http://www.muzhiwan.com/");
		Elements elements = doc.select("a.link");
		// doc.select("a[class=link]");
		for (Element element : elements) {
			element.attr("href");// 用于获取该属性的值
			element.text();// 用于获取该标签的值
		}
	}

	/**
	 * used to download something
	 */
	public static void downloadDemo(String downloadUrl, String fileName) {
		int retryTimes = 0;
		HttpGet get = new HttpGet(downloadUrl);
		get.setConfig(config);
		HttpResponse response = null;
		File binaryFile = null;
		FileOutputStream fos = null;
		try {
			response = client.execute(get);
			binaryFile = new File(fileName);
			if (!binaryFile.exists()) {
				binaryFile.createNewFile();
			}
			InputStream inputStream = response.getEntity().getContent();// 得到输入流，用于读取一个写入一个
			fos = new FileOutputStream(binaryFile);
			byte[] tmpBuf = new byte[1024];
			int bufLen = 0;
			while ((bufLen = inputStream.read(tmpBuf)) > 0) {
				fos.write(tmpBuf, 0, bufLen);
			}
		} catch (IOException e) {
			e.printStackTrace();
			if (retryTimes < 3) {
				downloadDemo(downloadUrl, fileName);
			}
		} finally {
			try {
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * used to post form data which is the url needed
	 */
	public static void postDemo(String url) {
		HttpPost post = new HttpPost(url);
		post.setConfig(config);
		post.setHeader("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.93 Safari/537.36");
		post.setHeader("Connection", "keep-alive");
		List<NameValuePair> list = new ArrayList<NameValuePair>();
		list.add(new BasicNameValuePair("key", "value"));
		list.add(new BasicNameValuePair("key", "value"));
		list.add(new BasicNameValuePair("key", "value"));
		list.add(new BasicNameValuePair("key", "value"));
		list.add(new BasicNameValuePair("key", "value"));
		try {
			HttpEntity entity = new UrlEncodedFormEntity(list, "utf-8");
			post.setEntity(entity);
			HttpResponse response = client.execute(post);
			String responseHtml = EntityUtils.toString(response.getEntity());
			System.out.println(responseHtml);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** use to handle cookies */
	public static void cookiesOperationDemo() {
		List<Cookie> list = cookieStore.getCookies();// get all cookies
		System.out.println("cookie is:");
		System.out.println("-----------------------");
		for (Cookie cookie : list) {
			System.out.println(cookie);
		}
		System.out.println("-----------------------");
		cookieStore.clear();
		BasicClientCookie cookie = new BasicClientCookie("name", "value");
		// new a cookie
		cookie.setDomain("domain");
		cookie.setExpiryDate(new Date());
		// set the properties of the cookie
		cookieStore.addCookie(cookie);
		// add this cookie to the cookiestore
	}

	/**
	 * this is a method for execute a get or post;if you want to execute a get
	 * method ,make the param list to be null;default timeout period is 6000ms.
	 * if you want to add some yourself headers,the third param is for u;else
	 * make it be null;
	 * 
	 * @author Mercy
	 */
	public static HttpResponse getResponse(String url, List<NameValuePair> list, Header[] headers) {
		RequestConfig config = RequestConfig.custom().setConnectTimeout(6000).setSocketTimeout(6000)
				.setCookieSpec(CookieSpecs.STANDARD_STRICT).setConnectionRequestTimeout(60000).build();
		// use the method setCookieSpec to make the header which named
		// set-cookie effect
		HttpResponse response = null;
		HttpUriRequest request = null;
		if (list == null) {
			HttpGet get = new HttpGet(url);
			get.setConfig(config);
			request = get;
		} else {
			HttpPost post = new HttpPost(url);
			post.setConfig(config);
			HttpEntity entity = null;
			try {
				entity = new UrlEncodedFormEntity(list, "utf-8");
				post.setEntity(entity);
				request = post;
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		request.setHeader("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.93 Safari/537.36");
		request.setHeader("Connection", "keep-alive");
		request.setHeaders(headers);
		try {
			System.out.println("ready to link " + url);
			response = client.execute(request);
			System.out.println("status code is " + response.getStatusLine().getStatusCode());
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return response;
	}

	public static void main(String[] args) {
		System.out.println("hello world!");
		getDemo(null);
	}

}
