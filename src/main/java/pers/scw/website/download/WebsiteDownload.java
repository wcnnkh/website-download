package pers.scw.website.download;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import scw.core.Constants;
import scw.core.utils.StringUtils;
import scw.http.HttpHeaders;
import scw.http.HttpResponseEntity;
import scw.http.HttpUtils;
import scw.http.client.exception.HttpClientException;
import scw.io.FileUtils;
import scw.io.support.TemporaryFile;
import scw.logger.Logger;
import scw.logger.LoggerUtils;
import scw.net.MimeType;

/**
 * 网页下载(注意：忽略了非同源地址)
 * 
 * @author shuchaowen
 *
 */
public class WebsiteDownload {
	private static Logger logger = LoggerUtils.getLogger(WebsiteDownload.class);
	private final String rootDirectory;
	private final String website;

	// 是否下载超链接内容
	private boolean downloadHyperlinks = true;

	// 是否下载图片
	private boolean downloadImage = true;

	// 是否下载多媒体内容
	private boolean downloadMultiMedia = true;

	// 下载失败后重试次数
	private int retryCount = 3;

	private String charsetName = Constants.UTF_8.name();
	
	private HttpHeaders httpHeaders = new HttpHeaders();

	/**
	 * 下载网站
	 * 
	 * @param rootDirectory
	 *            下载目录
	 * @param website
	 *            网站地址
	 */
	public WebsiteDownload(String rootDirectory, String website) {
		this.rootDirectory = rootDirectory;
		this.website = website;
	}

	public HttpHeaders getHttpHeaders() {
		return httpHeaders;
	}

	public boolean isDownloadHyperlinks() {
		return downloadHyperlinks;
	}

	public void setDownloadHyperlinks(boolean downloadHyperlinks) {
		this.downloadHyperlinks = downloadHyperlinks;
	}

	public String getCharsetName() {
		return charsetName;
	}

	public void setCharsetName(String charsetName) {
		this.charsetName = charsetName;
	}

	public boolean isDownloadImage() {
		return downloadImage;
	}

	public void setDownloadImage(boolean downloadImage) {
		this.downloadImage = downloadImage;
	}

	public boolean isDownloadMultiMedia() {
		return downloadMultiMedia;
	}

	public void setDownloadMultiMedia(boolean downloadMultiMedia) {
		this.downloadMultiMedia = downloadMultiMedia;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	protected void parse(Document document) {
		downloadByTag(document, "script", "src", "js");
		downloadByTag(document, "link", "href", "css");

		if (isDownloadImage()) {
			downloadByTag(document, "img", "src", "jpg");
		}

		if (isDownloadHyperlinks()) {
			downloadByTag(document, "a", "href", "html");
		}

		if (isDownloadMultiMedia()) {
			downloadByTag(document, "video", "poster", "jpg");
			downloadByTag(document, "source", "src", "mp4");
			downloadByTag(document, "video", "src", "mp4");
			downloadByTag(document, "audio", "src", "mp3");
		}
	}

	/**
	 * 下载
	 * 
	 * @throws IOException
	 */
	public void download() throws IOException {
		download(website, "html", true);
		logger.info("root directory: " + rootDirectory);
	}

	/**
	 * 根据标签名下载资源
	 * 
	 * @param root
	 * @param tagName
	 * @param attributeName
	 *            下载地址的属性名称
	 * @param ext
	 *            默认的文件扩展名
	 */
	protected void downloadByTag(Element root, String tagName, String attributeName, String ext) {
		Elements elements = root.getElementsByTag(tagName);
		for (Element element : elements) {
			if (element.hasAttr(attributeName)) {
				String path = null;
				String url = element.absUrl(attributeName);
				if (StringUtils.isEmpty(url)) {
					continue;
				}

				if (!HttpUtils.isSameOrigin(website, url)) {
					logger.trace("忽略非同源地址:{}", url);
					continue;
				}

				try {
					path = download(url, ext, false);
				} catch (Exception e) {
					logger.error(e, "download error {} [{}]", tagName, url);
				}

				if (path == null) {
					continue;
				}
				element.attr(attributeName, path);
			}
		}
	}

	/**
	 * 下载文件
	 * @param url
	 *            下载地址
	 * @param ext
	 *            文件默认扩展名
	 * @param force
	 *            是否强制重新下载
	 * @return
	 * @throws IOException
	 */
	protected String download(String url, String ext, boolean force) throws IOException {
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e1) {
			return null;
		}

		String path = uri.getPath();
		if (path.startsWith("/")) {
			path = path.substring(1);
		}

		if (StringUtils.isEmpty(path)) {
			path = "index";
		}
		
		if(path.endsWith("/")){
			path += "index";
		}

		String extToUse = StringUtils.getFilenameExtension(path);
		if (StringUtils.isNotEmpty(ext) && StringUtils.isEmpty(extToUse)) {
			extToUse = ext;
			path = path + "." + extToUse;
		}

		File file = new File(rootDirectory + "/" + path);
		if (!force && file.exists()) {
			logger.debug("忽略:{}", url);
			return path;
		}

		logger.info("开始下载:{}", url);
		HttpResponseEntity<TemporaryFile> httpResponseEntity = null;
		for (int i = 0; i <= getRetryCount(); i++) {
			try {
				httpResponseEntity = HttpUtils.getHttpClient().download(url, getHttpHeaders(), null, true);
				if (httpResponseEntity.getBody() != null) {
					FileUtils.copyFile(httpResponseEntity.getBody(), file, FileUtils.ONE_MB);
					break;
				}
				logger.error("下载[{}]失败准备第{}次重试", url, (i + 1));
			} catch (HttpClientException e) {
				logger.error(e, "下载[{}]失败准备第{}次重试", url, (i + 1));
			}
		}

		if (httpResponseEntity == null || httpResponseEntity.getBody() == null) {
			return null;
		}
		logger.info("下载成功{} ==> {}", url, path);
		if (file.getName().endsWith(".html")) {
			logger.info("开始解析：" + path);
			String website = url;
			int index = website.indexOf("?");
			if (index != -1) {
				website = website.substring(0, index);
			}

			index = website.indexOf("#");
			if (index != -1) {
				website = website.substring(0, index);
			}

			MimeType mimeType = httpResponseEntity.getHeaders().getContentType();
			String charsetName = null;
			if (mimeType != null) {
				charsetName = mimeType.getCharsetName();
			}

			if (StringUtils.isEmpty(charsetName)) {
				charsetName = getCharsetName();
			}
			Document document = Jsoup.parse(file, charsetName);
			document.setBaseUri(website);
			parse(document);
			FileUtils.write(file, document.html(), charsetName);
		}
		return path;
	}
}
