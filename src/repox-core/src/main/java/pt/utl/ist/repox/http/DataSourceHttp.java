/*
 * Created on 2007/01/23
 *
 */
package pt.utl.ist.repox.http;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import pt.utl.ist.repox.dataProvider.dataSource.FileRetrieveStrategy;
import pt.utl.ist.repox.util.ConfigSingleton;
import pt.utl.ist.repox.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;


public class DataSourceHttp implements FileRetrieveStrategy{
    private static final Logger log = Logger.getLogger(DataSourceHttp.class);

    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean retrieveFiles(String dataSourceId){
        createOutputDirPath(url, dataSourceId);

        String tempRepoxFolder = getOutputHttpPath(url, dataSourceId);

        try {
            URL sourceURL = new URL(url);
            InputStream inputStream = sourceURL.openStream();

            String outputFileName = url.substring(url.lastIndexOf("/")+1);


            File outuputFile = new File(tempRepoxFolder + "/" + outputFileName);
            FileOutputStream outPutStream = new FileOutputStream(outuputFile);
            int c;
            while ((c = inputStream.read()) != -1) {
                outPutStream.write(c);
            }
            inputStream.close();
            outPutStream.close();

            if(outuputFile.exists()){
                URLConnection urlConnection = sourceURL.openConnection();
                outuputFile.setLastModified(urlConnection.getLastModified());
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


    public static boolean createOutputDirPath(String url, String set){
        File output = new File(getOutputHttpPath(url, set));
        if(output.exists()){
            try {
                FileUtils.deleteDirectory(output);
                log.info("Deleted Data Source HTTP dir with success from Data Source.");

            } catch (IOException e) {
                log.error("Unable to delete Data Source HTTP dir from Data Source.");
            }
        }
        return output.mkdir();
    }

    public static String getOutputHttpPath(String url, String set){
        String httpRequestPath = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getConfiguration().getHttpRequestPath();

        String outputDirString = httpRequestPath
                + File.separator + FileUtil.sanitizeToValidFilename(url)
                + "-" + FileUtil.sanitizeToValidFilename(set);

        return outputDirString;
    }
    


    public DataSourceHttp(String url) {
        this.url = url;
    }
}


