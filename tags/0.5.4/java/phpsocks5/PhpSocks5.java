package phpsocks5;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

class PeerData
{

	public Socket peer;

	public DataOutputStream peerOut;

	public DataInputStream peerIn;

	public CookieHandler cookieHandler = new CookieHandler();

}

class Utils
{

	public static byte[] secretkey;

	public static String serverurl;

	public static byte[] prefix;

	public static byte[] postfix;

	public static String version = "03";

	public static byte[] encrypt(byte[] data)
	{
		for(int i = 0; i < data.length; i++)
			data[i] ^= secretkey[i % secretkey.length];
		return data;
	}

	public static byte[] decrypt(byte[] data)
	{
		return encrypt(data);
	}

	public static int readByte(InputStream in) throws IOException
	{
		int b = in.read();
		if(b == -1)
			throw new EOFException();
		return b;
	}

	public static StringBuilder toHex(byte[] buf, int len, StringBuilder sb)
	{
		if(sb == null)
			sb = new StringBuilder();
		else
			sb.delete(0, sb.length());
		for(int i = 0; i < len; i++)
		{
			sb.append(' ');
			sb.append(Integer.toHexString(buf[i] & 0xff));
		}
		sb.append('(');
		for(int i = 0; i < len; i++)
		{
			if(buf[i] < 32 || buf[i] > 126)
				sb.append('.');
			else
				sb.append((char)buf[i]);
		}
		sb.append(')');
		return sb;
	}

	private static URLConnection getURLConn() throws MalformedURLException, IOException
	{
		URLConnection conn = new URL(serverurl).openConnection();
		conn.setRequestProperty("Accept", "image/gif, image/x-xbitmap, image/jpeg, image/pjpeg, application/x-shockwave-flash, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, application/x-ms-application, application/x-ms-xbap, application/vnd.ms-xpsdocument, application/xaml+xml, */*");
		conn.setRequestProperty("Accept-Language", "zh-cn");
		conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
		conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1; .NET CLR 2.0.50727; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)");
		conn.setDoOutput(true);
		return conn;
	}

	public static InputStream getURLInput(URLConnection conn, String func) throws IOException
	{
		try
		{
			if("gzip".equalsIgnoreCase(conn.getHeaderField("Content-Encoding")))
				return new GZIPInputStream(conn.getInputStream());
			else
				return conn.getInputStream();
		}
		catch (IOException e)
		{
			HttpURLConnection hconn = (HttpURLConnection) conn;
			InputStream err = hconn.getErrorStream();
			if("gzip".equalsIgnoreCase(hconn.getHeaderField("Content-Encoding")))
				err = new GZIPInputStream(err);
			byte[] buf = new byte[4096];
			int len;
			System.err.print(new Date());
			System.err.print("\tException in ");
			System.err.print(func);
			System.err.print(": {encrypt=");
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			try
			{
				while((len = err.read(buf)) > 0)
					bout.write(buf, 0, len);
			}
			catch (Exception e1)
			{}
			buf = bout.toByteArray();
			StringBuilder sb = new StringBuilder();
			System.err.print(toHex(buf, buf.length, sb).toString());
			System.err.print(", decrypt=");
			System.err.print(toHex(decrypt(buf), buf.length, sb).toString());
			System.err.println("}");
			throw e;
		}
	}

	public static void connect(PeerData peerData, String host, int port) throws MalformedURLException, IOException
	{
		byte[] header = (version + "1" + host + ":" + port).getBytes();
		URLConnection conn = getURLConn();
		OutputStream out = conn.getOutputStream();
		out.write(encrypt(header));
		out.flush();
		peerData.cookieHandler.getCookie(conn);
		getURLInput(conn, "connect");
	}

	public static void background(PeerData peerData) throws MalformedURLException, IOException
	{
		byte[] header = (version + "2").getBytes();
		URLConnection conn = getURLConn();
		peerData.cookieHandler.putCookie(conn);
		OutputStream out = conn.getOutputStream();
		out.write(encrypt(header));
		out.flush();
		InputStream in = getURLInput(conn, "background");
		while((in.read()) >= 0);
	}

	public static void send(PeerData peerData, byte[] buf) throws MalformedURLException, IOException
	{
		byte[] header = (version + "3").getBytes();
		int len = peerData.peerIn.read(buf);
		if(len <= 0)
			throw new EOFException();
		URLConnection conn = getURLConn();
		peerData.cookieHandler.putCookie(conn);
		OutputStream out = conn.getOutputStream();
		byte[] data = new byte[header.length + len];
		System.arraycopy(header, 0, data, 0, header.length);
		System.arraycopy(buf, 0, data, header.length, len);
		System.err.print(peerData.peer.toString());
		System.err.print(" send:");
		System.err.println(toHex(data, data.length, null).toString());
		out.write(encrypt(data));
		out.flush();
		getURLInput(conn, "send");
	}

	public static int searchBytes(byte[] buf, byte[] search)
	{
		for(int i = 0; i < buf.length - search.length; i++)
		{
			int j = 0;
			for(; j < search.length; j++)
			{
				if(buf[i + j] != search[j])
					break;
			}
			if(j == search.length)
				return i;
		}
		return -1;
	}

	public static void receive(PeerData peerData, byte[] buf, ByteArrayOutputStream bout) throws MalformedURLException, IOException
	{
		byte[] header = (version + "4").getBytes();
		URLConnection conn = getURLConn();
		peerData.cookieHandler.putCookie(conn);
		OutputStream out = conn.getOutputStream();
		out.write(encrypt(header));
		out.flush();
		InputStream in = getURLInput(conn, "receive");
		int len;
		bout.reset();
		while((len = in.read(buf)) > 0)
			bout.write(buf, 0, len);
		buf = bout.toByteArray();
		StringBuilder sb = new StringBuilder();
		System.err.print(peerData.peer.toString());
		System.err.print(" receive: {encrypt=");
		System.err.print(toHex(buf, buf.length, sb).toString());
		System.err.print(", decrypt=");
		int pos = searchBytes(buf, prefix);
		System.arraycopy(buf, pos + prefix.length, buf, 0, buf.length - pos - prefix.length);
		pos = searchBytes(buf, postfix);
		peerData.peerOut.write(decrypt(buf), 0, pos);
		peerData.peerOut.flush();
		System.err.print(toHex(buf, pos, sb).toString());
		System.err.println("}");
	}

	public static void close(PeerData peerData) throws MalformedURLException, IOException
	{
		try
		{
			Thread.sleep(1000);
		}
		catch (InterruptedException e)
		{}
		try
		{
			peerData.peerOut.close();
		}
		catch (Exception e)
		{}
		try
		{
			peerData.peerIn.close();
		}
		catch (Exception e)
		{}
		try
		{
			peerData.peer.close();
		}
		catch (Exception e)
		{}
		byte[] header = (version + "5").getBytes();
		URLConnection conn = getURLConn();
		peerData.cookieHandler.putCookie(conn);
		OutputStream out = conn.getOutputStream();
		out.write(encrypt(header));
		out.flush();
		getURLInput(conn, "close");
	}

}

class CookieHandler
{

	private Map<String, String> cookies = new HashMap<String, String>();

	public void getCookie(URLConnection conn)
	{
		String setCookie = conn.getHeaderField("Set-Cookie");
		System.err.println("setCookie=" + setCookie);
		if(setCookie == null)
			return;
		String[] setCookies = setCookie.split("\\s*;\\s*");
		for(int i = 0; i < setCookies.length; i++)
		{
			String[] cookie = setCookies[i].split("\\s*=\\s*");
			cookie[0] = cookie[0].trim();
			if(cookie[0].equalsIgnoreCase("domain"))
				continue;
			if(cookie[0].equalsIgnoreCase("expires"))
				continue;
			if(cookie[0].equalsIgnoreCase("path"))
				continue;
			if(cookie[0].equalsIgnoreCase("secure"))
				continue;
			if(cookie.length != 2)
				cookies.put(cookie[0], null);
			else
				cookies.put(cookie[0], cookie[1]);
		}
	}

	public void putCookie(URLConnection conn)
	{
		String sCookie = "";
		for(Iterator<Map.Entry<String, String>> it = cookies.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<String, String> e = it.next();
			if(e.getValue() != null)
				sCookie += e.getKey() + "=" + e.getValue() + "; ";
			else
				sCookie += e.getKey() + "; ";
		}
		if(sCookie.length() > 0)
		{
			sCookie = sCookie.substring(0, sCookie.length() - 2);
			conn.setRequestProperty("Cookie", sCookie);
			System.err.println("sCookie=" + sCookie);
		}
	}

}

class PeerSender implements Runnable
{

	private PeerData peerData;

	public PeerSender(PeerData peerData)
	{
		this.peerData = peerData;
	}

	public void run()
	{
		byte[] buf = new byte[4096];
		try
		{
			Thread.sleep(100);
			while(true)
				Utils.send(peerData, buf);
		}
		catch (MalformedURLException e)
		{}
		catch (IOException e)
		{
			//e.printStackTrace();
		}
		catch (InterruptedException e)
		{}
		finally
		{
			try
			{
				Utils.close(peerData);
			}
			catch (MalformedURLException e)
			{}
			catch (IOException e)
			{}
		}
	}

}

class PeerReceiver implements Runnable
{

	private PeerData peerData;

	public PeerReceiver(PeerData peerData)
	{
		this.peerData = peerData;
	}

	public void run()
	{
		byte[] buf = new byte[4096];
		try
		{
			Thread.sleep(100);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			while(true)
				Utils.receive(peerData, buf, bout);
		}
		catch (MalformedURLException e)
		{}
		catch (IOException e)
		{
			//e.printStackTrace();
		}
		catch (InterruptedException e)
		{}
		finally
		{
			try
			{
				Utils.close(peerData);
			}
			catch (MalformedURLException e)
			{}
			catch (IOException e)
			{}
		}
	}

}

public class PhpSocks5 implements Runnable
{

	private PeerData peerData;

	public PhpSocks5(PeerData peerData)
	{
		this.peerData = peerData;
	}

	public static void main(String[] args) throws IOException
	{
		Properties props = new Properties();
		props.load(PhpSocks5.class.getResourceAsStream("/phpsocks5.properties"));
		props.store(System.err, "phpsocks5.properties");
		ServerSocket ss = new ServerSocket(Integer.parseInt(props.getProperty("localport")), 0, InetAddress.getByName(props.getProperty("localhost")));
		Utils.secretkey = props.getProperty("secretkey").getBytes();
		Utils.serverurl = props.getProperty("serverurl");
		Utils.prefix = props.getProperty("prefix").getBytes();
		Utils.postfix = props.getProperty("postfix").getBytes();
		while(true)
		{
			PeerData peerData = new PeerData();
			peerData.peer = ss.accept();
			peerData.peerOut = new DataOutputStream(peerData.peer.getOutputStream());
			peerData.peerIn = new DataInputStream(peerData.peer.getInputStream());
			new Thread(new PhpSocks5(peerData)).start();
		}
	}

	private static void socksResult(OutputStream out, int errno) throws IOException
	{
		out.write(5);
		out.write(errno);
		out.write(0);
		out.write(1);
		out.write(0);
		out.write(0);
		out.write(0);
		out.write(0);
		out.write(0);
		out.write(0);
		out.flush();
		if(errno != 0)
			throw new EOFException();
	}

	public void run()
	{
		byte[] buf = new byte[4096];
		try
		{
			Utils.readByte(peerData.peerIn);
			{
				int nmethods = Utils.readByte(peerData.peerIn);
				if(nmethods == -1)
					throw new EOFException();
				peerData.peerIn.readFully(buf, 0, nmethods);
			}
			peerData.peerOut.write(5);
			peerData.peerOut.write(0);
			peerData.peerOut.flush();
			Utils.readByte(peerData.peerIn);
			{
				int cmd = Utils.readByte(peerData.peerIn);
				if(cmd != 1)
					socksResult(peerData.peerOut, 7);
				Utils.readByte(peerData.peerIn);
				int atyp = Utils.readByte(peerData.peerIn);
				String host;
				switch(atyp)
				{
				case 1:
					byte[] addr = new byte[4];
					peerData.peerIn.readFully(addr);
					host = InetAddress.getByAddress(addr).getHostAddress();
					break;
				case 3:
					int addrLen = Utils.readByte(peerData.peerIn);
					peerData.peerIn.readFully(buf, 0, addrLen);
					host = new String(buf, 0, addrLen);
					break;
				default:
					socksResult(peerData.peerOut, 8);
					return;
				}
				int port = Utils.readByte(peerData.peerIn) << 8;
				port |= Utils.readByte(peerData.peerIn);
				Utils.connect(peerData, host, port);
			}
			socksResult(peerData.peerOut, 0);
			new Thread(new PeerSender(peerData)).start();
			new Thread(new PeerReceiver(peerData)).start();
			Utils.background(peerData);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				Utils.close(peerData);
			}
			catch (MalformedURLException e)
			{}
			catch (IOException e)
			{}
		}

	}

}
