package org.entermedia.fatwire;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.asset.Asset;
import org.entermediadb.asset.MediaArchive;
import org.openedit.OpenEditException;
import org.openedit.data.PropertyDetail;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.hittracker.HitTracker;
import org.openedit.hittracker.SearchQuery;
import org.openedit.page.manage.PageManager;
import org.openedit.users.User;
import org.openedit.users.UserManager;
import org.openedit.util.DateStorageUtil;
import org.openedit.util.PathUtilities;
import org.openedit.xml.XmlArchive;

import com.fatwire.rest.beans.AssetBean;
import com.fatwire.rest.beans.AssetInfo;
import com.fatwire.rest.beans.AssetsBean;
import com.fatwire.rest.beans.Association;
import com.fatwire.rest.beans.Associations;
import com.fatwire.rest.beans.Attribute;
import com.fatwire.rest.beans.Attribute.Data;
import com.fatwire.rest.beans.Site;
import com.fatwire.rest.beans.SitesBean;
import com.fatwire.wem.sso.SSO;
import com.fatwire.wem.sso.SSOException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

public class FatwireManager {
	private static final Log log = LogFactory.getLog(FatwireManager.class);
	protected Client fieldClient;
	//include SSOConfig.xml in /WEB-INF/classes/ folder
	protected String fieldSSOConfig = "SSOConfig.xml";
	protected MediaArchive fieldMediaArchive;
	protected XmlArchive fieldXmlArchive;
	protected UserManager fieldUserManager;
	protected FatwireUtil fieldFatwireUtil;
	
	public PageManager getPageManager()
	{
		return getMediaArchive().getPageManager();
	}
	
	public UserManager getUserManager()
	{
		return fieldUserManager;
	}
	
	public void setUserManager(UserManager inUserManager)
	{
		fieldUserManager = inUserManager;
	}
  
	public XmlArchive getXmlArchive() {
		return fieldXmlArchive;
	}

	public void setXmlArchive(XmlArchive inXmlArchive) {
		fieldXmlArchive = inXmlArchive;
	}

	public MediaArchive getMediaArchive()
	{
		return fieldMediaArchive;
	}

	public void setMediaArchive(MediaArchive inMediaArchive)
	{
		fieldMediaArchive = inMediaArchive;
	}
	
	public String getSSOConfig()
	{
		return fieldSSOConfig;
	}
	
	public void setSSOConfig(String inSSOConfig)
	{
		fieldSSOConfig = inSSOConfig;
	}
	
	public FatwireUtil getFatwireUtil()
	{
		if (fieldFatwireUtil == null)//lazy init
		{
			fieldFatwireUtil = new FatwireUtil();
		}
		return fieldFatwireUtil;
	}
	
	public void setFatwireUtil(FatwireUtil inFatwireUtil)
	{
		fieldFatwireUtil = inFatwireUtil;
	}
	
	public Client getClient()
	{
		if(fieldClient == null)
		{
			fieldClient = Client.create();
		}
		return fieldClient;
	}
	
	public String getAttribute(AssetBean inAsset, String inName)
	{
		List<Attribute> attributes = inAsset.getAttributes();
		for(Iterator<Attribute> iterator = attributes.iterator(); iterator.hasNext();)
		{
			Attribute attr = iterator.next();
			if(attr.getName().equalsIgnoreCase(inName))
			{
				return attr.getData().getStringValue();
			}
		}
		return null;
	}
	
	public List<String> getAttributeList(AssetBean inAsset, String inName)
	{
		ArrayList<String> attrs = new ArrayList<String>();
		List<Attribute> attributes = inAsset.getAttributes();
		for(Iterator<Attribute> iterator = attributes.iterator(); iterator.hasNext();)
		{
			Attribute attr = iterator.next();
			if(attr.getName().equalsIgnoreCase(inName))
			{
				attrs.add(attr.getData().getStringValue());
			}
		}
		return attrs;
	}
	
	public String getSite()
	{
		String catalogid = getMediaArchive().getCatalogId();
		org.openedit.Data catalog = (org.openedit.Data)getMediaArchive().getSearcherManager().getData("media", "catalogs", catalogid);
		return catalog.getName();
	}
	
	public AssetsBean search(String inSite, String inType, BigInteger inStartIndex)
	{
		Client client = getClient();
		String baseurl = getUrlBase();
		String url = baseurl + "/sites/" + inSite + "/types/" + inType + "/search?field:name:wildcard=*";
		if(inStartIndex != null)
		{
			url = url + "&startindex=" + inStartIndex;
		}
		WebResource wr = client.resource(url);
		String ticket = getTicket();
		wr = wr.queryParam("ticket", ticket);
		Builder builder = wr.header("Pragma", "auth-redirect=false");
		AssetsBean fwassets = builder.get(AssetsBean.class);
		return fwassets;
	}
	
	public void pullAssets()
	{
		pullImageAssets(getSite(), BigInteger.ZERO, null);
	}
	
	public void pullImageAssets(String inSite, BigInteger inStartIndex, BigInteger inTotal)
	{
		log.info("Called pullImageAssets with StartIndex, Total: " + inStartIndex + ", " + inTotal);
		if(inTotal != null && inTotal.compareTo(inStartIndex) <= 0)
		{
			//we hit the end of the list
			return;
		}
		
		//do a wildcard search and process each bean
		AssetsBean fwassets = search(inSite, "Image_C", inStartIndex);
		
		inTotal = fwassets.getTotal();
		
		//loop through count returned
		//search again with start index - 1
		for (AssetInfo assetinfo : fwassets.getAssetinfos()) {
			String id = assetinfo.getId();
			pullAsset(inSite, "Image_C", id.split(":")[1]);
		}
		pullImageAssets(inSite, inStartIndex.add(fwassets.getCount()), inTotal);
	}
	
	public void pullAsset(String inSite, String inType, String inId)
	{
		//first search for the asset
		Client client = getClient();
		String baseurl = getUrlBase();
		String url = baseurl + "/sites/" + inSite + "/types/" + inType + "/assets/" + inId;
		WebResource wr = client.resource(url);
		String ticket = getTicket();
		
		//@todo - change to multiticket
		wr = wr.queryParam("ticket", ticket);
		Builder builder = wr.header("Pragma", "auth-redirect=false");
		AssetBean fwasset = builder.get(AssetBean.class);
		
		if(fwasset == null || fwasset.getId() == null)
		{
			//asset doesn't exist
			return;
		}
		
		//see if we have an asset with this id
		SearchQuery q = getMediaArchive().getAssetSearcher().createSearchQuery();
		q.addExact("fatwireid", fwasset.getId());
		HitTracker hits = getMediaArchive().getAssetSearcher().search(q);
		
		if(hits.size() > 0)
		{
			//don't import asset is already here, in future merge properties
			log.info("Not importing asset, asset already exists in EnterMedia");
			return;
		}
		
		//lets go ahead and import it
		String imageurl = getAttribute(fwasset, "imageurl");
		if(imageurl == null)
		{
			return;
		}
		String pageName = PathUtilities.extractPageName(imageurl);
		Asset asset = getMediaArchive().createAsset("fatwire/" + inSite + "/" + inType + "/" + pageName);
		asset.setFolder(true);
		
		//lets go through and set some properties
		asset.setName(fwasset.getName());
		asset.setProperty("assettitle", fwasset.getDescription());
		
		//set up categories
		List<String> categories = getAttributeList(fwasset, "cat");
		for (String category : categories) {
			asset.addKeyword(category);
		}
		asset.setProperty("fatwireid", fwasset.getId());
		
		//save the asset
		getMediaArchive().getAssetSearcher().saveData(asset, null);
	}
	
	public void addCategory(AssetBean inFwAsset, String inKeyword)
	{
		Attribute catattr = new Attribute();
        Data catdata = new Data();
        catdata.setStringValue(inKeyword);
        catattr.setData(catdata);
        catattr.setName("cat");
        inFwAsset.getAttributes().add(catattr);
	}
	
	/**
	 * Push an asset to fatwire
	 * @param inArchive
	 * @param inAsset
	 * @param inData
	 * @throws IOException
	 */
	public void pushAsset(Asset inAsset, org.openedit.Data inData) throws IOException
	{
		//get site and type from data
		String site = inData.get("site");
		String type = inData.get("type");
		//build the fatwire asset from supplied arguments
		AssetBean fwasset = getFatwireUtil().buildAsset(getMediaArchive(), inAsset, inData);
		log.info("AssetBean SENT to fatwire:");
        printAssetBean(fwasset);
        //generate multiticket
		String multiticket = null;
		try{
			multiticket = getMultiTicket();
		}catch (SSOException e){
			log.error("SSOException caught getting multiticket, message=["+e.getMessage()+"]", e);
			String errorMessage = getFatwireUtil().formatErrorMessage(getMediaArchive(),e.getMessage());
			throw new IOException(errorMessage,e);
		}
		log.info("generated ticket from sso: "+multiticket);
		String urlbase = getUrlBase();
		//spin up client
		Client client = Client.create();
		WebResource webResource = client.resource(urlbase);
		webResource = webResource.queryParam("multiticket", multiticket);
        webResource = webResource.path("sites").path(site).path("types").path(type).path("assets").path("0");
        Builder builder = webResource.accept(MediaType.APPLICATION_JSON);//APPLICATION_XML throws a security exception, use JSON
        builder = builder.header("X-CSRF-Token", multiticket);//this is required
        
		AssetBean ab = null;
		try
		{
			ab = builder.put(AssetBean.class, fwasset);
			log.info("AssetBean RESPONSE from fatwire:");
			printAssetBean(ab);
		}
		catch (UniformInterfaceException e)
		{
			InputStream in = e.getResponse().getEntityInputStream();
			if( in != null)
			{
				String theString = IOUtils.toString(in, "utf-8");
				log.error("Got back" + theString);
			}
			log.error("UniformInterfaceException caught while issuing put(), message=["+e.getMessage()+"]", e);
			String errorMessage = getFatwireUtil().formatErrorMessage(getMediaArchive(),e.getMessage());
			throw new IOException(errorMessage,e);
		}
		//set the fatwireid in the data argument
		if (ab!=null && ab.getId()!=null)
		{
			inData.setProperty("fatwireid", ab.getId());
		}
	}
	
	/**
	 * @deprecated use pushAsset(archive,asset,data)
	 * @param inAsset
	 * @param inUser
	 * @param inUrlHome
	 * @param inUsage
	 * @param exportName
	 * @param outputFile
	 * @param inDimension
	 * @return
	 * @throws IOException
	 */
	public AssetBean pushAsset(Asset inAsset, User inUser, String inUrlHome, String inUsage, String exportName, String outputFile, Dimension inDimension) throws IOException
	{
		return pushAsset(inAsset, "CA", "Image_C", "Image", inUser, inUrlHome, inUsage, exportName, outputFile, inDimension);
	}
	
	/**
	 * @deprecated
	 * @param inAsset
	 * @param inType
	 * @param inSubtype
	 * @param inUser
	 * @param inUrlHome
	 * @param inUsage
	 * @return
	 * @throws IOException
	 */
	public AssetBean pushAsset(Asset inAsset, String inType, String inSubtype, User inUser, String inUrlHome, String inUsage) throws IOException
	{
		return pushAsset(inAsset, getSite(), inType, inSubtype, inUser, inUrlHome, inUsage, null, null, null);
	}
	
	/**
	 * @deprecated
	 * @param inAsset
	 * @param inSite
	 * @param inType
	 * @param inSubtype
	 * @param inUser
	 * @param inUrlHome
	 * @param inUsage
	 * @param inExportName
	 * @param inOutputFile
	 * @param inDimension
	 * @return
	 * @throws IOException
	 */
	public AssetBean pushAsset(Asset inAsset, String inSite, String inType, String inSubtype, User inUser, String inUrlHome, String inUsage, String inExportName, String inOutputFile, Dimension inDimension) throws IOException
	{
		log.info("pushAsset ("+(inAsset!=null ? inAsset.getId() : "null")+","+
				(inSite)+","+(inType)+","+(inSubtype)+","+(inUser!=null ? inUser.getName() : "null")+","+
				(inUrlHome)+","+(inUsage)+","+(inExportName)+","+(inOutputFile)+"),("+inDimension+")");
		
		//convert our asset in to an asset bean
		AssetBean fwasset = new AssetBean();
		if (inExportName!=null && !inExportName.isEmpty())
		{
			fwasset.setName(inExportName);
		}
		else 
		{
			fwasset.setName(inAsset.getName());//make sure the name is filled in
		}
		if (inAsset.get("assettitle") == null || !inAsset.get("assettitle").isEmpty())
		{
			fwasset.setDescription(fwasset.getName());//make sure description is filled in
		}
		else
		{
			fwasset.setDescription(inAsset.get("assettitle"));
		}
		fwasset.setSubtype(inSubtype);
		fwasset.getPublists().add(inSite);
		
		//this should be configurable
//		String thumbpath = "/image/EM/thumb_"+inExportName;
		String originalpath = "/image/EM/"+inExportName;
		
		String width = inDimension!=null ? String.valueOf((int) inDimension.getWidth()) : inAsset.get("width");
		String height = inDimension!=null ? String.valueOf( (int) inDimension.getHeight()) : inAsset.get("height");
		
		//these should be covered in configurable fields
//		String alttext = inAsset.get("headline");
//		String usagerights = (inUsage == null || inUsage.isEmpty() ? "0" : inUsage);
//		String artist = inAsset.get("artist");
		
		StringBuilder buf = new StringBuilder();
		Collection<String> keywords = inAsset.getKeywords();
		for (String keyword : keywords) {
			buf.append(keyword).append(",");
		}
		String keywordlist = "0";
		if (!keywords.isEmpty())
		{
			keywordlist = buf.toString().substring(0, buf.toString().length()-1);
		}
		//list of attribute name:value pairs
        String[][] attributes = {
			{"source","0"},//0 by default
			{"thumbnailurl",originalpath},//Dec 16 - testing use of original path for thumbnail url
			{"imageurl",originalpath},
			{"width","int:"+ (width == null || width.isEmpty() ? "0" : width)},
			{"height","int:"+(height == null || height.isEmpty() ? "0" : height)},
			{"keywords",keywordlist}
        };
        for (int i=0; i<attributes.length;i++)
        {
        	String name = attributes[i][0];
        	String stringvalue = attributes[i][1];
        	boolean useInt = false;
        	if (stringvalue == null || stringvalue.isEmpty()){
        		stringvalue = "0";
        	}	
        	else if (stringvalue.startsWith("int:"))
        	{
        		String str = stringvalue.substring("int:".length());
        		try{
        			stringvalue = String.valueOf(Integer.parseInt(str));
        			useInt = true;
        		}
        		catch (Exception e){}
        	}
        	log.info("adding "+name+":"+stringvalue+" to fatwire assetbean");
        	Attribute sourceAssetAttribute = new Attribute();
            Data sourceAssetAttributeData = new Data();
            sourceAssetAttribute.setName(name);
            if (useInt)
            {
            	sourceAssetAttributeData.setIntegerValue(new Integer(Integer.parseInt(stringvalue)));
            }
            else 
            {
            	sourceAssetAttributeData.setStringValue(stringvalue);
            }
            sourceAssetAttribute.setData(sourceAssetAttributeData);
            fwasset.getAttributes().add(sourceAssetAttribute);
        }
        //check for additional fields
        Collection details = getMediaArchive().getAssetSearcher().getPropertyDetails();
        if (details.size() > 0) {
			for (Iterator iterator = details.iterator(); iterator.hasNext();) {
				PropertyDetail detail = (PropertyDetail) iterator.next();
				String name = detail.get("fatwirefield");
				if(name != null){
					List<String> list = findDefaultValue(name,inAsset);
					String fatwirefields = list.get(0);
					String defaultvalue = list.size()==2 ? list.get(1) : null;
					log.info("values for fatwirefield["+name+"]: "+fatwirefields+" ("+defaultvalue+")");
					String stringvalue = inAsset.get(detail.getId());
					ArrayList<String> fields = findKeys(fatwirefields,",");
					for (String fatwirefield:fields) {
						if (fatwirefield.isEmpty()){
							continue;
						}
						if (isDefaultAttribute(fatwirefield,attributes)){
							log.info("skipping "+fatwirefield+", has already been added by default");
							continue;
						}
						Attribute sourceAssetAttribute = new Attribute();
						Data sourceAssetAttributeData = new Data();
						sourceAssetAttribute.setName(fatwirefield);
						if(detail.isList()){
							SearcherManager sm = getMediaArchive().getSearcherManager();
							Searcher searcher = sm.getSearcher(getMediaArchive().getCatalogId(), detail.getListId());
							org.openedit.Data remote = (org.openedit.Data) searcher.searchById(stringvalue);
							if (remote == null){
								remote = (org.openedit.Data) searcher.searchByField("default", "true");
								if (remote == null){//no default found so return what?
									//if stringvalue is not valid, then return defaultvalue
									stringvalue = generateValue(stringvalue,defaultvalue);
									sourceAssetAttributeData.setStringValue(stringvalue);
									log.info("adding " + fatwirefield + ":" + stringvalue+ " to fatwire assetbean (property is list, null)");
								} else {
									if(remote.get("fatwirevalue") != null && !remote.get("fatwirevalue").isEmpty()){
										sourceAssetAttributeData.setStringValue( remote.get("fatwirevalue") );
										log.info("adding " + fatwirefield + ":" + remote.get("fatwirevalue")+ " (default value) to fatwire assetbean (property is list, found default)");
									} else{
										//if stringvalue is not valid, then return defaultvalue
										stringvalue = generateValue(stringvalue,defaultvalue);
										sourceAssetAttributeData.setStringValue(stringvalue);
										log.info("adding " + fatwirefield + ":" + stringvalue+ " to fatwire assetbean (property is list, no default value set)");
									}
								}
							} else if(remote.get("fatwirevalue") != null && !remote.get("fatwirevalue").isEmpty()){
								sourceAssetAttributeData.setStringValue( remote.get("fatwirevalue") );
								log.info("adding " + fatwirefield + ":" + remote.get("fatwirevalue")+ " to fatwire assetbean (property is list, found fatwire value)");
							} else{
								//if stringvalue is not valid, then return defaultvalue
								stringvalue = generateValue(stringvalue,defaultvalue);
								sourceAssetAttributeData.setStringValue(stringvalue);
								log.info("adding " + fatwirefield + ":" + stringvalue+ " to fatwire assetbean (property is list, no fatwirevalue found");
							}
						} else if (detail.isDate()) {
							//currently cannot support a default type for dates
							Date date = DateStorageUtil.getStorageUtil().parseFromStorage(stringvalue);
							sourceAssetAttributeData.setDateValue(date);
							log.info("adding " + fatwirefield + ":" + date+ " to fatwire assetbean (property is date)");
						} else {
							//if stringvalue is not valid, then return defaultvalue
							stringvalue = generateValue(stringvalue,defaultvalue);
							sourceAssetAttributeData.setStringValue(stringvalue);
							log.info("adding " + fatwirefield + ":" + stringvalue+ " to fatwire assetbean (property is string)");
						}
						sourceAssetAttribute.setData(sourceAssetAttributeData);
						fwasset.getAttributes().add(sourceAssetAttribute);
					}
				}
			}
        }
        
        log.info("AssetBean SENT to fatwire:");
        printAssetBean(fwasset);
        
		String multiticket = getTicket();
		log.info("generated ticket from sso: "+multiticket);
		String urlbase = getUrlBase();
		
		Client client = Client.create();
		WebResource webResource = client.resource(urlbase);
		webResource = webResource.queryParam("multiticket", multiticket);
        webResource = webResource.path("sites").path(inSite).path("types").path(inType).path("assets").path("0");
        Builder builder = webResource.accept(MediaType.APPLICATION_JSON);//APPLICATION_XML throws a security exception, use JSON
        builder = builder.header("X-CSRF-Token", multiticket);//this is required
        
		AssetBean ab = null;
		try
		{
			ab = builder.put(AssetBean.class, fwasset);
			log.info("AssetBean RESPONSE from fatwire:");
			printAssetBean(ab);
		}
		catch (UniformInterfaceException e)
		{
			log.error("UniformInterfaceException caught while issuing put(), message=["+e.getMessage()+"]", e);
			String errorMessage = formatErrorMessage(e.getMessage());
			throw new IOException(errorMessage,e);
		}
		return ab;
	}
	
	protected String formatErrorMessage(String inMessage){
		Searcher searcher = getMediaArchive().getSearcherManager().getSearcher(getMediaArchive().getCatalogId(),"fatwireexception");
		HitTracker hits = searcher.getAllHits();
		if (hits!=null){
			for (Iterator itr = hits.iterator(); itr.hasNext(); ){
				org.openedit.Data data = (org.openedit.Data) itr.next();
				log.info("formatting error message, found "+data.getName()+", "+data.get("errorcode"));
				if (inMessage.contains(data.get("errorcode"))){
					log.info("message ["+inMessage+"] contains error code "+data.get("errorcode")+", returning "+data.get("message"));
					return data.get("message");
				}
				log.info("message ["+inMessage+"] does not contain error code "+data.get("errorcode"));
			}
		}
		log.info("message ["+inMessage+"] does not contain any hits, returning default");
		if (inMessage.contains("returned a response status of"))
		{
			return "UniformInterfaceException "+inMessage.substring(inMessage.indexOf("returned a response status of"));
		}
		return inMessage;
	}
	
	protected boolean isDefaultAttribute(String inFatwirefield,String [][] inDefaultAttributes){
		if (inDefaultAttributes == null){
			return false;
		}
		for (String [] attribute:inDefaultAttributes){
			if (attribute[0].equals(inFatwirefield))
				return true;
		}
		return false;
	}
	
	protected String generateValue(String inValue, String inDefault){
		if (inValue == null){
			return inDefault;
		}
		return inValue;
	}
	
	protected List<String> findDefaultValue(String inCode, Asset inAsset)
	{
		//pattern: fatwire-external-ids,default={$a||$b||$c||d}
		List<String> list = new ArrayList<String>();
		int startindex = -1;
		int endindex = -1;
		if ( (startindex = inCode.indexOf("default=")) > -1 && (endindex = inCode.lastIndexOf("}")) > -1 && startindex < endindex){
			String difference = new StringBuilder().append(inCode.substring(0,startindex)).append(inCode.substring(endindex+"}".length())).toString().trim();
			if (difference.endsWith(",")) {
				difference = difference.substring(0,difference.length() - ",".length());
			}
			list.add(difference);
			String defaultValue = null;
			String tokens = inCode.substring(startindex + "default=".length(), endindex).replace("{", "").replace("}","");
			List<String> fields = findKeys(tokens,"||");
			for (String field:fields){
				if (field.startsWith("$")){
					field = field.substring("$".length());
					if (inAsset.get(field)!=null && !inAsset.get(field).isEmpty()){
						defaultValue = inAsset.get(field);
						break;
					}
					continue;
				}
				defaultValue = field;
				break;
			}
			if (defaultValue!=null) list.add(defaultValue);
		} else {
			list.add(inCode);
		}
		return list;
	}
	
	protected ArrayList<String> findKeys(String Subject, String Delimiters) 
    {
		StringTokenizer tok = new StringTokenizer(Subject, Delimiters);
		ArrayList<String> list = new ArrayList<String>(Subject.length());
		while(tok.hasMoreTokens()){
			list.add(tok.nextToken());
		}
		return list;
    }
	
	public List<Site> getSites()
	{
		String baseurl = getUrlBase();
		String url = baseurl + "/sites";
		
		Client client = getClient();
		WebResource wr = client.resource(url);
		//we need to set the ticket for authentication
		String ticket = getTicket();//url, getSSOConfig());
		wr = wr.queryParam("ticket", ticket);
		//make sure we don't redirect
		Builder builder = wr.header("Pragma", "auth-redirect=false");
		//make the call
		SitesBean sb = builder.get(SitesBean.class);
		return sb.getSites();
	}
	
	public String getUrlBase()
	{
		SearcherManager sm = getMediaArchive().getSearcherManager();
		Searcher searcher = sm.getSearcher(getMediaArchive().getCatalogId(), "publishdestination");
		org.openedit.Data data = (org.openedit.Data) searcher.searchByField("publishtype", "fatwire");
		if (data == null) return null;
		return data.get("server");
	}
	
	public String getUserName() throws OpenEditException
	{
		SearcherManager sm = getMediaArchive().getSearcherManager();
		Searcher searcher = sm.getSearcher(getMediaArchive().getCatalogId(), "publishdestination");
		org.openedit.Data data = (org.openedit.Data) searcher.searchByField("publishtype", "fatwire");
		if (data == null){ throw new OpenEditException();};
		return data.get("username");
	}
	
	public String getTicket()
	{
		return getTicket(null);
	}
	
	public String getTicket(User inUser)
	{
		String username = null;
		String password = null;
		try {
			
		
		if (inUser == null)
		{
			username = getUserName();
			User user = getUserManager().getUser(username);
			password = getUserManager().decryptPassword(user);
		}
		else
		{
			username = inUser.getUserName();
			password = getUserManager().decryptPassword(inUser);
		}
		} catch (OpenEditException e) {
			// TODO: handle exception
		}
		
		String config = getSSOConfig();
		log.info("Getting SSO Session ticket ("+config+", "+username+")");
		try {
			String ticket = SSO.getSSOSession(config).getMultiTicket(username, password);
			return ticket;
		} catch (SSOException e) {
			log.error("unable to generate SSO Session Ticket, "+e.getMessage(),e);
			
			 // Troubleshooting SSOException
            // ============================
            //
            // Cause: CAS is not running.
            // Remedy: Deploy CAS and start up the application server for CAS
            // webapp.
            //
            // Cause: CAS is not configured.
            // Remedy: Verify that the URL in the CAS config file,
            // ExampleCASConfig.xml, is reachable.
            //
            // Cause: Username / password is invalid.
            // Remedy: Use valid username / password to authenticate against
            // CAS.
		}
		return null;
	}
	
	//use the following capture SSO Exceptions
	public String getMultiTicket() throws SSOException
	{
		String username = getUserName();
		User user = getUserManager().getUser(username);
		String password = getUserManager().decryptPassword(user);
		String config = getSSOConfig();
		log.info("Getting SSO Session multi-ticket ("+config+", "+username+")");
		String ticket = SSO.getSSOSession(config).getMultiTicket(username, password);
		return ticket;
	}
	
	public static void printAssetBean(AssetBean bean)
	{
		StringBuilder buf = new StringBuilder();
		if (bean == null)
		{
			buf.append("AssetBean is NULL");
		}
		else
		{
			buf.append("AssetBean data\n");
			buf.append("\tid:\t").append(bean.getId()).append("\n");
			buf.append("\tname:\t").append(bean.getName()).append("\n");
			buf.append("\tcreatedby:\t").append(bean.getCreatedby()).append("\n");
			buf.append("\tdescription:\t").append(bean.getDescription()).append("\n");
			buf.append("\tstatus:\t").append(bean.getStatus()).append("\n");
			buf.append("\tsubtype:\t").append(bean.getSubtype()).append("\n");
			buf.append("\tupdatedby:\t").append(bean.getUpdatedby()).append("\n");
			if (bean.getAssociations()!=null) buf.append(getAssociationsStr(bean.getAssociations()));
			if (bean.getAttributes()!=null) buf.append(getAttributesStr(bean.getAttributes()));
			if (bean.getPublists()!=null) buf.append(getPublistsStr(bean.getPublists()));
		}
		log.info(buf.toString().trim());
	}
	
	public static String getAssociationsStr(Associations associations)
	{
		StringBuilder buf = new StringBuilder();
		if (associations==null || associations.getAssociations()==null){
			buf.append("\tassociation:\t[]\n");
		} else {
			Iterator<Association> itr = associations.getAssociations().iterator();
			while(itr.hasNext())
			{
				buf.append("\tassociation:\t"+itr.next().getName()).append("\n");
			}
		}
		return buf.toString();
	}
	
	public static String getAttributesStr(List<Attribute> list)
	{
		StringBuilder buf = new StringBuilder();
		Iterator<Attribute> itr = list!=null ? list.iterator() : null;
		while(itr!=null && itr.hasNext())
		{
			Attribute attr = itr.next();
			Data data = attr.getData();
			String val = (data!=null ? data.getStringValue() : "[null]");
			if (val == null || val.equals("null"))
			{
				val = data.getIntegerValue()!=null ? data.getIntegerValue().toString() : "null";
			}
			if (val == null || val.equals("null"))
			{
				val = data.getDateValue()!=null ? data.getDateValue().toString() : "null";
			}
			buf.append("\tattribute:\t"+attr.getName()+"\t"+val).append("\n");
		}
		return buf.toString();
	}
	
	public static String getPublistsStr(List<String> list)
	{
		StringBuilder buf = new StringBuilder();
		Iterator<String> itr = list !=null ? list.iterator() : null;
		while(itr !=null && itr.hasNext())
		{
			buf.append("\tpublist:\t"+itr.next()).append("\n");
		}
		return buf.toString();
	}
}
