package io.github.wcnnkh.website.download;

import java.util.Scanner;

import io.basc.framework.env.MainArgs;
import io.basc.framework.http.HttpHeaders;
import io.basc.framework.io.FileUtils;
import io.basc.framework.logger.Logger;
import io.basc.framework.logger.LoggerFactory;
import io.basc.framework.net.uri.UriUtils;
import io.basc.framework.util.StringUtils;
import io.basc.framework.value.Value;

public class WebsiteDownloadApplication {
	private static Logger logger = LoggerFactory.getLogger(WebsiteDownloadApplication.class);
	private static final String QUIT = "q";
	private static final String BEGIN = "请输入要下载的网站, 输入'" + QUIT +"'即可退出";
	
	public static void main(String[] args) {
		MainArgs mainArgs = new MainArgs(args);
		Value d = mainArgs.getNextValue("-d");
		String directory = d == null? null:d.getAsString();
		if (StringUtils.isEmpty(directory)) {
			directory = FileUtils.getUserDir();
		}
		
		Scanner scanner = new Scanner(System.in);
		logger.info(BEGIN);
		while(true){
			String input;
			try {
				input = scanner.next();
			} catch (Exception e) {
				logger.info("程序非正常退出(可能使用了ctrl+c)");
				break;
			}
			
			if(input.equals(QUIT)){
				logger.info("你输入了{}，程序已退出", QUIT);
				break;
			}
			
			if(!input.startsWith("http://") && !input.startsWith("https://")){
				logger.error("错误的网站地址!");
				continue;
			}
			
			WebsiteDownload websiteDownload = new WebsiteDownload(directory + "/" + UriUtils.encode(input), input);
			//使用百度爬虫的UA可以绕过一些网站的验证(如:微博的访客系统)
			websiteDownload.getHttpHeaders().set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; Baiduspider-render/2.0; +http://www.baidu.com/search/spider.html)");
			try {
				websiteDownload.download();
			} catch (Exception e) {
				logger.error(e, "download error : {}", input);
			}
			logger.info(BEGIN);
		}
		scanner.close();
	}
}
