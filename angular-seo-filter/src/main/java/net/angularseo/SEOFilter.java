package net.angularseo;

import java.io.File;
import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.angularseo.crawler.CachePageManager;
import net.angularseo.crawler.CrawlRequest;
import net.angularseo.crawler.CrawlTaskManager;
import net.angularseo.util.URLUtils;
import net.angularseo.util.UserAgentUtil;

import org.openqa.selenium.remote.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet Filter implementation class RobotFilter
 */
public class SEOFilter implements Filter {

    // The time to wait js dynamic page finish loading
    private static int Default_WAIT_FOR_PAGE_LOAD = 5;
    // The interval the crawler re-crawl the site to generate the static page
    // the unit is hour
    private static int DEFAULT_CACHE_TIMEOUT = 24;

    private Logger logger = LoggerFactory.getLogger(SEOFilter.class);

    private boolean isFirst = true;

    private boolean isNeedFilter = true;

    /**
     * Default constructor.
     */
    public SEOFilter() {
    }

    /**
     * @see Filter#init(FilterConfig)
     */
    public void init(FilterConfig fConfig) throws ServletException {
        if (!isNeedFilter)
            return;
        //配置文件中是否需要过滤
        String seoSwitch = fConfig.getInitParameter("seofilter.switch");
        if (seoSwitch != null) {
            if (!Boolean.parseBoolean(seoSwitch)) {
                isNeedFilter = false;
                return;
            }
        }
        // Set the execute path of phantomjs
        String phanatomPath = System.getenv("phantomjs");
        if (phanatomPath == null)
            phanatomPath = fConfig.getInitParameter("phantomjs.binary.path");
        if (phanatomPath == null) {
            logger.warn("the phantomjs.binary.path param for RobotFilter is not define,it will not use SEOFilter...");
            //throw new UnavailableException("Please set the phantomjs.binary.path param for RobotFilter in web.xml");
            isNeedFilter = false;
            return;
        }
        File f = new File(phanatomPath);
        if (!f.exists()) {
            logger.warn("Cannot find phantomjs binary in given RobotFilter phantomjs.binary.path,,it will not use SEOFilter...");
            //throw new UnavailableException("Cannot find phantomjs binary in given RobotFilter phantomjs.binary.path " + phanatomPath);
            isNeedFilter = false;
            return;
        }
        System.setProperty("phantomjs.binary.path", phanatomPath);

        // Set the time to wait the page finish loading
        String waitForPageLoadStr = fConfig.getInitParameter("waitForPageLoad");
        int waitForPageLoad = Default_WAIT_FOR_PAGE_LOAD;
        if (waitForPageLoadStr != null) {
            try {
                waitForPageLoad = Integer.parseInt(waitForPageLoadStr);
            } catch (NumberFormatException e) {
            }
        }

        // Set customize robot user agent
        String robotUserAgent = fConfig.getInitParameter("robotUserAgents");
        UserAgentUtil.initCustomizeAgents(robotUserAgent);

        // Get cache timeout, crawler will re-crawl when cache timeout
        String cacheTimeoutStr = fConfig.getInitParameter("cacheTimeout");
        int cacheTimeout = DEFAULT_CACHE_TIMEOUT;
        if (cacheTimeoutStr != null) {
            try {
                cacheTimeout = Integer.parseInt(cacheTimeoutStr);
            } catch (NumberFormatException e) {
            }
        }

        // Get cache path
        String cachePath = fConfig.getInitParameter("cachePath");
        if (cachePath == null) {
            logger.info("the cachePath param for RobotFilter in web.xml is not defined,it will use default path...");
            //throw new UnavailableException("Please set the cachePath param for RobotFilter in web.xml");
            cachePath = System.getProperty("java.io.tmpdir");
            if (cachePath == null)
                cachePath = ".//seo//cache";
        }

        // Get the default encoding of site
        String encoding = fConfig.getInitParameter("encoding");
        if (encoding == null) {
            encoding = "UTF-8";
        }

        // Get crawl depth
        String crawlDepthStr = fConfig.getInitParameter("crawlDepth");
        int crawlDepth = 2;
        if (crawlDepthStr != null) {
            try {
                crawlDepth = Integer.parseInt(crawlDepthStr);
            } catch (NumberFormatException e) {
            }
        }

        logger.info("RobotFilter started with {}, {}, {}, {}, {}", phanatomPath, waitForPageLoad, robotUserAgent, cacheTimeout, cachePath);

        AngularSEOConfig config = AngularSEOConfig.getConfig();
        config.cachePath = cachePath;
        config.cacheTimeout = cacheTimeout;
        config.waitForPageLoad = waitForPageLoad;
        config.encoding = encoding;
        config.crawlDepth = crawlDepth;

        CrawlTaskManager.getInstance().schedule();
    }

    /**
     * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        //不需要过滤
        if (!isNeedFilter) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        //忽略POST 请求
        if ("POST".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        //忽略css/js/jpg/png等静态文件
        String rootUrl = req.getRequestURL().toString();
         String lastIndex = rootUrl.substring(rootUrl.lastIndexOf(".") + 1, rootUrl.length());
        if ("css".equalsIgnoreCase(lastIndex) || "js".equalsIgnoreCase(lastIndex) ||
                "jpg".equalsIgnoreCase(lastIndex) || "png".equalsIgnoreCase(lastIndex) ||
                "gif".equalsIgnoreCase(lastIndex)) {
            chain.doFilter(request, response);
            return;
        }
        //本地启动不需要缓存
        if(rootUrl.contains("localhost")||rootUrl.contains("127.0.0.1"))
        {
            chain.doFilter(request, response);
            return;
        }
        //url 中包括IP
        if(isContainIp(rootUrl))
        {
            chain.doFilter(request, response);
            return;
        }
        //第一次触发缓存后，就不需要处理
        if (isFirst) {
            if(rootUrl.contains("443"))//https
            {
                rootUrl = rootUrl.replaceFirst("http(://[^:]*).*", "https$1");
            }else {//http
                rootUrl = rootUrl.replaceFirst("(http://[^/]*).*", "$1");
            }
            AngularSEOConfig.getConfig().setRootURL(rootUrl);
            isFirst = false;
        }

        String userAgent = req.getHeader("User-Agent");
        if (UserAgentUtil.isRobot(req) && isTextRequest(req)) {
            logger.info("Search engine robot request: {}", userAgent);
            logger.info("Load static html for robot: " + (req.getRequestURL().toString() + "?" + req.getQueryString()));
            String url = req.getRequestURL().toString();
            if(url.contains("443")) {//https请求
                url = url.replace(":443", "");
                url=url.replace("http","https");
            }
            if (req.getQueryString() != null) {
                url += "?" + req.getQueryString();
            }
            String html = CachePageManager.get(req);
            if (html == null) {
                // Crawl it then it can be crawled next time
                CrawlTaskManager.getInstance().addCrawlRequest(new CrawlRequest(url, 0));
                chain.doFilter(request, response);
            } else {
                response.setCharacterEncoding(AngularSEOConfig.getConfig().encoding);
                response.getWriter().write(html);
            }
        } else {
            String url = req.getRequestURL().toString();
            // _23 _21  _23_21
            if (URLUtils.isFromSearchEngine(url)) {
                String redirectUrl = URLUtils.toHashBang(url);
                redirectUrl += "?" + req.getQueryString();
                ((HttpServletResponse) response).sendRedirect(redirectUrl);
            } else {
                chain.doFilter(request, response);
            }
        }
    }

    /**
     * Check if the request is for html page as far as possible
     */
    private boolean isTextRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int p = uri.lastIndexOf("/");
        // requst for site default page
        if (p < 0) {
            return true;
        }

        String file = uri.substring(p + 1);
        p = file.indexOf(".");
        // without extention, usually is a html request
        if (p < 0) {
            return true;
        }

        String ext = file.substring(p + 1);
        if ("html".equals(ext) || "htm".equals(ext) || "jsp".equals(ext)) {
            return true;
        }

        return false;
    }

    /**
     * @see Filter#destroy()
     */
    public void destroy() {
    }
    public  boolean isContainIp(String uri){//判断是否是一个IP
        if(uri.contains("http:"))
            uri=uri.replaceFirst("http://([^/]*).*","$1");
        else if(uri.contains("https:"))
            uri=uri.replaceFirst("https://([^/]*).*","$1");
        boolean b = false;
        if(uri.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")){
            String s[] = uri.split("\\.");
            if(Integer.parseInt(s[0])<255)
                if(Integer.parseInt(s[1])<255)
                    if(Integer.parseInt(s[2])<255)
                        if(Integer.parseInt(s[3])<255)
                            b = true;
        }
        return b;
    }
}
