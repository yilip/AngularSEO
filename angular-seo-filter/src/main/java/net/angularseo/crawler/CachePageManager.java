package net.angularseo.crawler;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Joiner;
import net.angularseo.AngularSEOConfig;
import net.angularseo.SEOFilter;
import net.angularseo.util.URLUtils;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachePageManager {
	private static Logger logger = LoggerFactory.getLogger(SEOFilter.class);
	
	public static CachePageManager instance;
	private File cacheFolder;
	
	private CachePageManager(File cacheFolder) {
		this.cacheFolder = cacheFolder;
	}
	
	public synchronized static void init(File cacheFolder) {
		if (instance != null) {
			return;
		}
		
		instance = new CachePageManager(cacheFolder);
	}
	
	public static void save(String url, String pageSource, String encoding) {
		if (instance == null) {
			return;
		}
		
		url = URLUtils.escapeHashBang(url);
		String name = u2f(url);
		File f = new java.io.File(instance.cacheFolder, name);
		try {
			FileUtils.write(f, pageSource, encoding);
		} catch (IOException e) {
			logger.warn("Save static page {} failed: {}", name, e.getMessage());
		}
	}
	
	public static String get(HttpServletRequest req) {
		String url = req.getRequestURL().toString();
		if(url.contains("443")) {//https请求
			url = url.replace(":443", "");
			url=url.replace("http","https");
		}
		if (req.getQueryString() != null) {
			url += "?" + req.getQueryString();
		}
		String pageSource = "<html><body></body></html>";
		if (instance == null) {
			return pageSource;
		}
		
		String name = u2f(url);
		File f = new java.io.File(instance.cacheFolder, name);
		try {
			pageSource = FileUtils.readFileToString(f, AngularSEOConfig.getConfig().encoding);
		} catch (IOException e) {
			logger.warn("Load static page {} failed: {}", name, e.getMessage());
			final Map<String, String> parameterMap = new HashMap<String, String>();
			for(Map.Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
				parameterMap.put(entry.getKey(), entry.getValue() != null ? Joiner.on(',').join(entry.getValue()) : "");
			}
			logger.warn(String.format("Exception ocurred for %s, Referer: %s, User-Agent: %s, parameters: %s",
					req.getRequestURI(), req.getHeader("Referer"), req.getHeader("User-Agent"), parameterMap),  e);
		}
		return pageSource;
	}
	
	/**
	 *  Url name to file name
	 */
	public static String u2f(String url) {
		if(url.contains("https"))
			url = url.replaceFirst("https://[^/]*/?", "/");
		else
			url = url.replaceFirst("https://[^/]*/?", "/");
		String name = url.replaceAll("[\\\\/:\\*\\?<>|\"]", "_");
		name += ".html";
		return name;
	}
	
	public static void main(String[] args) {
		String str = "\\/:*?<>|\"";
		str = str.replaceAll("[\\\\/:\\*\\?<>|\"]", "_");
		System.out.println(str);
		
		String url = "http://www.abc.com/abc";
		url = url.replaceFirst("http://[^/]*/?", "/");
		System.out.println(url);
		
		url = "http://www.abc.com/abc?http://www.abc.com/";
		url = url.replaceFirst("(http://[^/]*).*", "$1");
		System.out.println(url);
		
		url = "http://www.abc.com/_23_21/a.html";
		String reg = "[\\_23|\\_21]+";
		System.out.println(url.matches(reg));
		
		url = "http://www.abc.com/_23/a.html";
		reg = "_23|_21";
		System.out.println(url.matches(reg));
		
		url = "http://www.abc.com/_21/a.html";
		reg = "_23|_21";
		System.out.println(url.matches(reg));
		
		url = "http://www.abc.com/#/a.html";
		reg = "_23|_21";
		System.out.println(url.matches(reg));
	}
}
