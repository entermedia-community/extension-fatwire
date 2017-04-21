package org.entermedia.fatwire;

import java.awt.Dimension;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Date;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.io.Util;
import org.entermediadb.asset.Asset;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.convert.ConversionUtil;
import org.entermediadb.asset.publishing.BasePublisher;
import org.entermediadb.asset.publishing.PublishResult;
import org.entermediadb.asset.publishing.Publisher;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.data.BaseData;
import org.openedit.data.Searcher;
import org.openedit.hittracker.SearchQuery;
import org.openedit.page.Page;
import org.openedit.users.User;
import org.openedit.users.UserManager;
import org.openedit.util.DateStorageUtil;

public class FatwirePublisher extends BasePublisher implements Publisher {
	private static final Log log = LogFactory.getLog(FatwirePublisher.class);

	/**
	 * Package all relevant variables required by FatwireManager
	 * 
	 * @param mediaArchive
	 * @param inAsset
	 * @param inPublishRequest
	 * @param inDestination
	 * @param inPreset
	 * @return
	 */
	public Data getInOutData(MediaArchive mediaArchive, Asset inAsset, Data inPublishRequest, Data inDestination,
			Data inPreset) {
		// add some hard-coded values for backwards compatibility
		final String DEFAULT_TYPE = "Image_C";// defaulttype
		final String DEFAUL_SUBTYPE = "Image";// defaultsubtype
		final String DEFAULT_REGION = "CA";// region or site
		final String DEFAULT_IMG_PATH = "/image/EM/";// defaultimagepath
		final String DEFAULT_MAX_EXPORT_LENGTH = "100";// maxexportlength
		final String DEFAULT_SOURCE = "0";// defaultsource
		final String DEFAULT_SPACE_REPLACE = " ";// exportspacesubstitution:
													// default is don't replace

		// get fatwire publish destination entry
		Searcher publishdestinationsearch = mediaArchive.getSearcherManager().getSearcher(mediaArchive.getCatalogId(),
				"publishdestination");
		SearchQuery fatwirequery = publishdestinationsearch.createSearchQuery().append("name", "FatWire");
		Data fatwireData = publishdestinationsearch.searchByQuery(fatwirequery);
		if (fatwireData == null) {
			log.info("critical error: fatwire is not configured as a publisher, aborting");
			return null;
		}
		String type = fatwireData.get("defaulttype") == null ? DEFAULT_TYPE : fatwireData.get("defaulttype");
		String subtype = fatwireData.get("defaultsubtype") == null ? DEFAUL_SUBTYPE : fatwireData.get("defaultsubtype");
		String imagepath = fatwireData.get("defaultimagepath") == null ? DEFAULT_IMG_PATH
				: fatwireData.get("defaultimagepath");
		String maxlength = fatwireData.get("maxexportlength") == null ? DEFAULT_MAX_EXPORT_LENGTH
				: fatwireData.get("maxexportlength");
		String source = fatwireData.get("defaultsource") == null ? DEFAULT_SOURCE : fatwireData.get("defaultsource");
		String spacechar = fatfatwireManagerwireData.get("exportspacesubstitution") == null ? DEFAULT_SPACE_REPLACE
				: fatwireData.get("exportspacesubstitution");

		int maxlen = Integer.parseInt(maxlength);

		// start setting properties on object
		Data inoutdata = new BaseData();
		// the following are known immediately
		inoutdata.setProperty("type", type);
		inoutdata.setProperty("subtype", subtype);

		inoutdata.setProperty("source", source);
		// region
		String regionid = inPublishRequest.get("regionid");
		if (regionid == null) {
			Searcher fatwireregionsearch = mediaArchive.getSearcherManager().getSearcher(mediaArchive.getCatalogId(),
					"fatwireregion");
			Data defaultfr = (Data) fatwireregionsearch.searchByField("default", "true");
			if (defaultfr != null) {
				regionid = defaultfr.getId();
			} else {
				regionid = DEFAULT_REGION;
			}
		}
		inoutdata.setProperty("site", regionid);
		// image path
		if (imagepath.contains("${") && imagepath.endsWith("}")) {
			String prefix = imagepath.substring(0, imagepath.indexOf("${")).replace("${", "").trim();
			String suffix = imagepath.substring(imagepath.indexOf("${")).replace("${", "").replace("}", "").trim();
			String substring = inPublishRequest.get(suffix);
			if (substring != null) {
				imagepath = prefix + substring;
			} else {
				imagepath = prefix.trim();
			}
			if (!imagepatfatwireManagerh.endsWith("/")) {
				imagepath = imagepath + "/";
			}
		}
		inoutdata.setProperty("imagepath", imagepath);
		// get dimension of published image: width & height
		String width = inAsset.get("width") == null ? "0" : inAsset.get("width");
		String height = inAsset.get("height") == null ? "0" : inAsset.get("height");
		String presetid = inPublishRequest.get("presetid");
		if (presetid == null) {
			log.info("critical error: presetid cannot be found, aborting");
			return null;
		}
		// use the dimension defined by the preset
		ConversionUtil cutil = (ConversionUtil) mediaArchive.getModuleManager().getBean("conversionUtil");
		Dimension dimension = cutil.getConvertPresetDimension(mediaArchive.getCatalogId(), presetid);
		if (dimension != null) {
			width = String.valueOf((int) dimension.getWidth());
			height = String.valueOf((int) dimension.getHeight());
		}
		inoutdata.setProperty("width", width);
		inoutdata.setProperty("height", height);

		// exportname
		String exportname = inPublishRequest.get("exportname");
		if (exportname == null) {
			log.info("critical error: exportname cannot be found, aborting");
			return null;
		}
		Searcher presetsearch = mediaArchive.getSearcherManager().getSearcher(mediaArchive.getCatalogId(),
				"convertpreset");
		Data presetdata = (Data) presetsearch.searchById(presetid);
		String pattern = presetdata.get("fileexportformat");
		String newexportname = reformat(exportname, pattern, spacechar, maxlen);
		if (newexportname != exportname) {
			exportname = newexportname;
			// Searcher pqsearcher =
			// mediaArchive.getSearcherManager().getSearcher(mediaArchive.getCatalogId(),
			// "publishqueue");
			// Data pqdata = pqsearcher.searchById(inPublishRequest.getId());
			// pqdata.setProperty("exportname", exportname);
			// pqsearcher.saveData(pqdata, null);
			inPublishRequest.setProperty("exportname", exportname);
		}
		inoutdata.setProperty("exportname", exportname);

		String description = inAsset.get("assettitle");
		if (description == null || description.isEmpty()) {
			description = exportname;// default
		}
		inoutdata.setProperty("description", description);

		return inoutdata;
	}

	public PublishResult publish(MediaArchive mediaArchive, Asset inAsset, Data inPublishRequest, Data inDestination,
			Data inPreset) {
		PublishResult result = new PublishResult();
		Data inoutdata = getInOutData(mediaArchive, inAsset, inPublishRequest, inDestination, inPreset);
		if (inoutdata == null) {
			log.info("internal error: unable to publish to fatwire");
			result.setComplete(true);
			result.setErrorMessage("Error publishing to FatWire: variables have not been set");
			return result;
		}
		// this does the actual publishing
		// FatwireManager fatwireManager = (FatwireManager)
		// mediaArchive.getModuleManager().getBean( "fatwireManager");
		FatwireManager fatwireManager = (FatwireManager) mediaArchive.getModuleManager().getBean("fatwireManager");
		fatwireManager.setMediaArchive(mediaArchive);
		try {
			fatwireManager.pushAsset(inAsset, inoutdata);
			if (inoutdata.get("fatwireid") != null) {
				String newId = inoutdata.get("fatwireid");
				inPublishRequest.setProperty("trackingnumber", newId);
				inPublishRequest.setProperty("date", DateStorageUtil.getStorageUtil().formatForStorage(new Date()));
				inPublishRequest.setProperty("regionid", inoutdata.get("site"));

				// ftp images to fatwire server
				Searcher publishdestinationsearch = mediaArchive.getSearcherManager()
						.getSearcher(mediaArchive.getCatalogId(), "publishdestination");
				SearchQuery fatwirequery = publishdestinationsearch.createSearchQuery().append("name", "FatWire");
				Data fatwireData = publishdestinationsearch.searchByQuery(fatwirequery);

				String ftpServer = fatwireData.get("ftpserver");
				String ftpUsername = fatwireData.get("ftpusername");

				UserManager usermanager = (UserManager) mediaArchive.getModuleManager().getBean("userManager");
				User ftpUser = usermanager.getUser(ftpUsername);
				String ftpPwd = usermanager.decryptPassword(ftpUser);

				Page original = findInputPage(mediaArchive, inAsset, inPreset);
				String exportpath = inoutdata.get("imagepath");
				String exportname = inoutdata.get("exportname");
				String sitestring = inoutdata.get("site");

				String fullexportname = sitestring + "/" + exportname;

				log.info("preparing to ftp, image " + original + " to " + fullexportname);
				ftpPublish(ftpServer, ftpUsername, ftpPwd, original, fullexportname, result);
				result.setComplete(true);
			} else {
				log.info("Error publishing asset: asset bean is NUll");
				result.setComplete(true);
				result.setErrorMessage("Error publishing to FatWire: unable to publish asset");
			}
		} catch (IOException e) {
			log.error(e.getMessage(), e);
			result.setComplete(true);
			result.setErrorMessage(e.getMessage());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			result.setComplete(true);
			result.setErrorMessage(e.getMessage());
		}
		return result;
	}

	public void ftpPublish(String servername, String username, String password, Page page, String export,
			PublishResult result) {
		log.info("ftpPublish  " + servername + " " + username+ " " + page+ " " + export);

		FTPClient ftp = new FTPClient();
		OutputStream os = null;

		try {
			try {
				ftp.connect(servername, 21);
				ftp.enterLocalPassiveMode();
				int reply = ftp.getReplyCode();
				String replymsg = ftp.getReplyString().trim();
				log.info("ftp client reply=" + reply + ", message=" + replymsg + ", is positive code? "
						+ FTPReply.isPositiveCompletion(reply));

				if (!FTPReply.isPositiveCompletion(reply)) {
					result.setErrorMessage(replymsg);
					ftp.disconnect();
					return;
				}
				ftp.login(username, password);
				reply = ftp.getReplyCode();
				replymsg = ftp.getReplyString().trim();
				log.info("ftp client reply=" + reply + ", message=" + replymsg.trim() + ", is positive code? "
						+ FTPReply.isPositiveCompletion(reply));
				if (!FTPReply.isPositiveCompletion(reply)) {
					result.setErrorMessage(replymsg);
					ftp.disconnect();
					return;
				}
				ftp.setFileTransferMode(FTPClient.BINARY_FILE_TYPE);

				long filelen = page.length();
				ftp.setFileType(FTPClient.BINARY_FILE_TYPE);

				log.info("### starting to export to " + export);
				os = ftp.storeFileStream(export);
				log.info("### got io stream");
				long copylen = Util.copyStream(page.getInputStream(), os);
				log.info("### file export " + export + " size=" + filelen + ", copy size=" + copylen);
			} finally {

				try {
					if (page.getInputStream() != null) {
						page.getInputStream().close();
					}
				} catch (Exception e) {
				}
				try {
					if (os != null) {
						os.close();
					}
				} catch (Exception e) {
				}
				try {
					ftp.completePendingCommand();
				} catch (Exception e) {
					log.error(e.getMessage(), e);
					try {
						ftp.disconnect();
					} catch (Exception e2) {
					}
					throw new OpenEditException(e);
				}
			}

			int reply = ftp.getReplyCode();
			String replymsg = ftp.getReplyString().trim();
			boolean ispositive = FTPReply.isPositiveCompletion(reply);
			log.info("ftp client following file copy of " + export + " reply=" + reply + ", message=" + replymsg
					+ ", ispositive=" + ispositive);

			if (!ispositive) {
				result.setErrorMessage(replymsg);
				ftp.disconnect();
				return;
			}
			if (ftp.isConnected()) {
				ftp.disconnect();
			}
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String reformat(String inValue, String inPattern, String spacereplace, int inLen){
                boolean replaceSpaces = (!spacereplace.trim().isEmpty());
                String finalValue = null;
                if (inValue.length() <= inLen || inLen < 0)
                {
                        finalValue = inValue;
                }
                else
                {
                        String part1 = null;
                        String part2 = null;
                        if (inPattern!=null)
                        {
                                StringTokenizer toks = new StringTokenizer(inPattern,"$",false);
                                while(toks.hasMoreTokens())
                                {//break on the first token that contains '}' somewhere in middle
                                        String tok = toks.nextToken();
                                        if (tok.contains("}") && !tok.endsWith("}"))
                                        {
                                                String sub = tok.substring(tok.indexOf("}") + 1);
                                                int i = inValue.indexOf(sub);
                                                part1 = inValue.substring(0,i);
                                                part2 = inValue.substring(i);
                                                break;
                                        }
                                }
                        }
                        if (part1!=null && part2!=null)
                        {
                                int trimto = inLen - part2.length();
                                String newpart = part1.substring(0,trimto);
                                finalValue = newpart + part2;
                        }
                        else
                        {
                                int len = inValue.length() - inLen;
                                finalValue = inValue.substring(len);
                        }
                }
                if (replaceSpaces && finalValue.contains(" "))
                {
                        //don't have duplicate space replacement chars
                        StringBuilder buf = new StringBuilder();
                        StringTokenizer toks = new StringTokenizer(finalValue," ",false);
                        while(toks.hasMoreTokens())
                        {
                                String tok = toks.nextToken();
                                buf.append(tok);
                                if (toks.hasMoreTokens())
                                {
                                        buf.append(spacereplace);
                                }
                        }
                        //do it again on the space replacement chars
                        toks = new StringTokenizer(buf.toString(),spacereplace,false);
                        buf.delete(0,buf.toString().length());
                        while(toks.hasMoreTokens())
                        {
                                String tok = toks.nextToken();
                                buf.append(tok);
                                if (toks.hasMoreTokens())
                                {
                                        buf.append(spacereplace);
                                }
                        }
                        return buf.toString();
                }
                return finalValue;
        }
}
