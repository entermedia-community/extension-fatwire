package org.entermedia.fatwire;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.data.PropertyDetail;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.entermedia.Asset;
import org.openedit.entermedia.MediaArchive;
import org.openedit.util.DateStorageUtil;

import com.fatwire.rest.beans.AssetBean;
import com.fatwire.rest.beans.Association;
import com.fatwire.rest.beans.Associations;
import com.fatwire.rest.beans.Attribute;
import com.fatwire.rest.beans.Attribute.Data;
import com.openedit.hittracker.HitTracker;

public class FatwireUtil {
	
	private static final Log log = LogFactory.getLog(FatwireUtil.class);
	
	public AssetBean buildAsset(MediaArchive inArchive, Asset inAsset, org.openedit.Data inData)
	{
		String width = inData.get("width");
		String height =  inData.get("height");
		String site = inData.get("site");
		String type = inData.get("type");
		String subtype = inData.get("subtype");
		String exportname = inData.get("exportname"); //already correct length by this point
		String imagepath = inData.get("imagepath"); //eg. "/image/EM/"
		String source = inData.get("source");//default 0
		String description = inData.get("description");//already defined by this point
		
		StringBuilder sb = new StringBuilder();
		sb.append("Dimension: ").append(width).append("x").append(height).append("\n");
		sb.append("Pub: ").append(site).append("/").append(type).append("/").append(subtype).append("\n");
		sb.append("Path: ").append(imagepath).append(exportname).append("\n");
		sb.append("Source: ").append(source).append("\n");
		sb.append("Description: ").append(description);
		
		log.info("setting the following properties: \n"+sb.toString());
		
		AssetBean fwasset = new AssetBean();
		fwasset.setName(exportname);
		fwasset.setDescription(description);
		fwasset.setSubtype(subtype);
		fwasset.getPublists().add(site);
		
		
		String path = new StringBuilder().append(imagepath).append(exportname).toString();
		
		StringBuilder buf = new StringBuilder();
		List<String> keywords = inAsset.getKeywords();
		for (String keyword : keywords)
		{
			buf.append(keyword).append(",");
		}
		String keywordlist = "0";
		if (!keywords.isEmpty())
		{
			keywordlist = buf.toString().substring(0, buf.toString().length()-1);
		}
		//list of attribute name:value pairs
        String[][] attributes = {
			{"source",source},
			{"thumbnailurl",path},
			{"imageurl",path},
			{"width","int:"+width},
			{"height","int:"+height},
			{"keywords",keywordlist}
        };
        for (int i=0; i<attributes.length;i++)
        {
        	String name = attributes[i][0];
        	String stringvalue = attributes[i][1];
        	boolean useInt = false;
        	if (stringvalue == null || stringvalue.isEmpty())
        	{
        		stringvalue = "0";
        	}	
        	else if (stringvalue.startsWith("int:"))
        	{
        		String str = stringvalue.substring("int:".length());
        		stringvalue = String.valueOf(Integer.parseInt(str));
    			useInt = true;
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
        Collection details = inArchive.getAssetSearcher().getPropertyDetails();
        if (details.size() > 0)
        {
			for (Iterator iterator = details.iterator(); iterator.hasNext();)
			{
				PropertyDetail detail = (PropertyDetail) iterator.next();
				String name = detail.get("fatwirefield");
				if(name != null)
				{
					List<String> list = findDefaultValue(name,inAsset);
					String fatwirefields = list.get(0);
					String defaultvalue = list.size()==2 ? list.get(1) : null;
					log.info("values for fatwirefield["+name+"]: "+fatwirefields+" ("+defaultvalue+")");
					String stringvalue = inAsset.get(detail.getId());
					ArrayList<String> fields = findKeys(fatwirefields,",");
					for (String fatwirefield:fields)
					{
						if (fatwirefield.isEmpty())
						{
							continue;
						}
						if (isDefaultAttribute(fatwirefield,attributes))
						{
							log.info("skipping "+fatwirefield+", has already been added by default");
							continue;
						}
						Attribute sourceAssetAttribute = new Attribute();
						Data sourceAssetAttributeData = new Data();
						sourceAssetAttribute.setName(fatwirefield);
						if(detail.isList())
						{
							SearcherManager sm = inArchive.getSearcherManager();
							Searcher searcher = sm.getSearcher(inArchive.getCatalogId(), detail.getListId());
							org.openedit.Data remote = (org.openedit.Data) searcher.searchById(stringvalue);
							if (remote == null)
							{
								remote = (org.openedit.Data) searcher.searchByField("default", "true");
								if (remote == null)
								{
									//if stringvalue is not valid, then return defaultvalue
									stringvalue = generateValue(stringvalue,defaultvalue);
									sourceAssetAttributeData.setStringValue(stringvalue);
									log.info("adding " + fatwirefield + ":" + stringvalue+ " to fatwire assetbean (property is list, null)");
								} else {
									if(remote.get("fatwirevalue") != null && !remote.get("fatwirevalue").isEmpty())
									{
										sourceAssetAttributeData.setStringValue( remote.get("fatwirevalue") );
										log.info("adding " + fatwirefield + ":" + remote.get("fatwirevalue")+ " (default value) to fatwire assetbean (property is list, found default)");
									} else {
										//if stringvalue is not valid, then return defaultvalue
										stringvalue = generateValue(stringvalue,defaultvalue);
										sourceAssetAttributeData.setStringValue(stringvalue);
										log.info("adding " + fatwirefield + ":" + stringvalue+ " to fatwire assetbean (property is list, no default value set)");
									}
								}
							} else if(remote.get("fatwirevalue") != null && !remote.get("fatwirevalue").isEmpty()) {
								sourceAssetAttributeData.setStringValue( remote.get("fatwirevalue") );
								log.info("adding " + fatwirefield + ":" + remote.get("fatwirevalue")+ " to fatwire assetbean (property is list, found fatwire value)");
							} else {
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
        return fwasset;
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
	
	public String formatErrorMessage(MediaArchive inArchive, String inMessage){
		Searcher searcher = inArchive.getSearcherManager().getSearcher(inArchive.getCatalogId(),"fatwireexception");
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
	
	public String getAssetBeanDetails(AssetBean inBean)
	{
		StringBuilder buf = new StringBuilder();
		if (inBean == null)
		{
			buf.append("AssetBean is NULL");
		}
		else
		{
			buf.append("AssetBean data\n");
			buf.append("\tid:\t").append(inBean.getId()).append("\n");
			buf.append("\tname:\t").append(inBean.getName()).append("\n");
			buf.append("\tcreatedby:\t").append(inBean.getCreatedby()).append("\n");
			buf.append("\tdescription:\t").append(inBean.getDescription()).append("\n");
			buf.append("\tstatus:\t").append(inBean.getStatus()).append("\n");
			buf.append("\tsubtype:\t").append(inBean.getSubtype()).append("\n");
			buf.append("\tupdatedby:\t").append(inBean.getUpdatedby()).append("\n");
			buf.append(getAssociationsStr(inBean.getAssociations()));
			buf.append(getAttributesStr(inBean.getAttributes()));
			buf.append(getPublistsStr(inBean.getPublists()));
		}
		return buf.toString();
	}
	
	protected String getAssociationsStr(Associations associations)
	{
		StringBuilder buf = new StringBuilder();
		if (associations==null || associations.getAssociations()==null || associations.getAssociations().isEmpty()){
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
	
	protected String getAttributesStr(List<Attribute> list)
	{
		StringBuilder buf = new StringBuilder();
		if (list==null || list.iterator()==null || list.isEmpty()){
			buf.append("\tattributes:\t[]\n");
		} else {
			Iterator<Attribute> itr = list.iterator();
			while(itr.hasNext())
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
		}
		return buf.toString();
	}
	
	protected String getPublistsStr(List<String> list)
	{
		StringBuilder buf = new StringBuilder();
		if (list==null || list.iterator()==null || list.isEmpty()){
			buf.append("\tpublist:\t[]\n");
		} else {
			Iterator<String> itr = list.iterator();
			while(itr !=null && itr.hasNext())
			{
				buf.append("\tpublist:\t"+itr.next()).append("\n");
			}
		}
		return buf.toString();
	}

}
