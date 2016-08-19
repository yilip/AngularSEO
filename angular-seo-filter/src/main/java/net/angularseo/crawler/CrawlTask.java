package net.angularseo.crawler;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import net.angularseo.AngularSEOConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrawlTask extends TimerTask {
	private Logger logger = LoggerFactory.getLogger(CrawlTask.class);
	
	/**
	 * The task to crawl static pages
	 */
	public CrawlTask() {
	}
	
	@Override
	public void run() {
		logger.info("AngularSEO crawl task starting...");

		// Crawl whole site
		CrawlTaskManager manager = CrawlTaskManager.getInstance();
		
		// clear cralwed urls
		manager.clearUrls();
		
		AngularSEOConfig config = AngularSEOConfig.getConfig();
		while (config.getRootURL() == null) {
			try {
				Thread.sleep(5000);
			} catch (Exception e) {
			}
		}
		
		manager.addCrawlRequest(new CrawlRequest(config.getRootURL(), config.crawlDepth - 1));
		
		// Check if all tasks finished
		while (!manager.isFinished()) {
			try {
				Thread.sleep(5000);
			} catch (Exception e) {
			}
		}
		
		// Update the time of this cache
		//manager.updateCachedTime();
		
		// Schedule next crawl
		Timer timer = new Timer();
		//long cacheTimeout = AngularSEOConfig.getConfig().cacheTimeout * 3600 * 1000L;
		Calendar calendar = Calendar.getInstance();
		int seconds = calendar.get(Calendar.HOUR_OF_DAY) * 60*60 + calendar.get(Calendar.MINUTE)*60+calendar.get(Calendar.SECOND);
		long nextTime = (24 * 60*60 - seconds)*1000l ;
        timer.schedule(new CrawlTask(), nextTime==0?24*60*60*1000l:nextTime);
		logger.info("AngularSEO crawl task will  work at next day 00:00" );
	}
}
