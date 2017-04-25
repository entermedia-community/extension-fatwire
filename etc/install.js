importPackage( Packages.org.openedit.util );
importPackage( Packages.java.util );
importPackage( Packages.java.lang );
importPackage( Packages.org.entermediadb.modules.update );


var war = "http://dev.entermediasoftware.com/jenkins/job/extension-fatwire/lastSuccessfulBuild/artifact/deploy/extension-fatwire.zip";

var root = moduleManager.getBean("root").getAbsolutePath();
var web = root + "/WEB-INF";
var tmp = web + "/tmp";

log.info("1. GET THE LATEST WAR FILE");
var downloader = new Downloader();
downloader.download( war, tmp + "/extension-fatwire.zip");

log.info("2. UNZIP WAR FILE");
var unziper = new ZipUtil();
unziper.unzip(  tmp + "/extension-fatwire.zip",  tmp );

log.info("3. REPLACE LIBS");
var files = new FileUtils();
files.deleteMatch (web + "/lib/extension-fatwire*.jar");

files.deleteMatch (web + "/lib/cas-client-core-3.1.9.jar");
files.deleteMatch (web + "/lib/jersey-client-1.1.4.1.jar");
files.deleteMatch (web + "/lib/jersey-core-1.1.4.1.jar");
files.deleteMatch (web + "/lib/jersey-json-1.1.4.1.jar");
files.deleteMatch (web + "/lib/jsr311-api-1.1.1.jar");
files.deleteMatch (web + "/lib/rest-api-1.2.2.jar");
files.deleteMatch (web + "/lib/rest-api-local-impl-1.2.2.jar");
files.deleteMatch (web + "/lib/wem-sso-api-1.2.2.jar");
files.deleteMatch (web + "/lib/wem-sso-api-cas-1.2.2.jar");
files.deleteMatch (web + "/lib/wem-sso-api-cas-plugin-cs-1.2.2.jar");
files.deleteMatch (web + "/lib/wem-sso-api-oam-1.2.2.jar");
files.deleteMatch (web + "/lib/wem-sso-cas-integration-rest-1.2.jar");
files.deleteMatch (web + "/lib/xwork-2.0.4.jar");
files.deleteMatch (web + "/lib/spring-*.jar");

files.copyFileByMatch( tmp + "/lib/extension-fatwire*.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/cas-client-core-3.1.9.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jersey-client-1.1.4.1.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jersey-core-1.1.4.1.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jersey-json-1.1.4.1.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/jsr311-api-1.1.1.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/rest-api-1.2.2.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/rest-api-local-impl-1.2.2.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/wem-sso-api-1.2.2.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/wem-sso-api-cas-1.2.2.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/wem-sso-api-cas-plugin-cs-1.2.2.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/wem-sso-api-oam-1.2.2.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/wem-sso-cas-integration-rest-1.2.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/xwork-2.0.4.jar", web + "/lib/");
files.copyFileByMatch( tmp + "/lib/spring-*.jar", web + "/lib/");


files.deleteMatch( web + "/WEB-INF/base/fatwire/")
files.copyFileByMatch( tmp + "/base/fatwire/", root + "/WEB-INF/base/fatwire/");


log.info("5. CLEAN UP");
files.deleteAll(tmp);

log.info("6. UPGRADE COMPLETED");