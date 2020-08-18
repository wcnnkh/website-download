package pers.scw.website.download;

import java.util.Scanner;

import scw.application.MainArgs;
import scw.core.utils.StringUtils;
import scw.http.HttpUtils;
import scw.logger.Logger;
import scw.logger.LoggerUtils;
import scw.value.Value;
import scw.value.property.SystemPropertyFactory;

public class WebsiteDownloadApplication {
	private static Logger logger = LoggerUtils.getLogger(WebsiteDownloadApplication.class);
	private static final String QUIT = "q";
	private static final String BEGIN = "请输入要下载的网站, 输入'" + QUIT +"'即可退出";
	
	public static void main(String[] args) {
		MainArgs mainArgs = new MainArgs(args);
		Value d = mainArgs.getInstruction("-d");
		String directory = d == null? null:d.getAsString();
		if (StringUtils.isEmpty(directory)) {
			directory = SystemPropertyFactory.getInstance().getUserDir();
		}
		
		Scanner scanner = new Scanner(System.in);
		logger.info(BEGIN);
		while(true){
			String input = scanner.next();
			if(input.equals(QUIT)){
				logger.info("你输入了{}，程序已退出", QUIT);
				break;
			}
			
			if(!input.startsWith("http://") && !input.startsWith("https://")){
				logger.error("错误的网站地址!");
				continue;
			}
			
			WebsiteDownload websiteDownload = new WebsiteDownload(directory + "/" + HttpUtils.encode(input), input);
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
