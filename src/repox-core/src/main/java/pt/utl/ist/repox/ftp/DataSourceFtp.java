/*
 * Created on 2007/01/23
 *
 */
package pt.utl.ist.repox.ftp;

import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.log4j.Logger;
import pt.utl.ist.repox.dataProvider.dataSource.FileRetrieveStrategy;
import pt.utl.ist.repox.util.ConfigSingleton;
import pt.utl.ist.repox.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class DataSourceFtp implements FileRetrieveStrategy{
    private static final Logger log = Logger.getLogger(DataSourceFtp.class);


    public static final String NORMAL = "Normal";
    public static final String ANONYMOUS = "Anonymous";

    private String server;
    private String user;
    private String password;
    private String idTypeAccess; // "Normal" or "Anonymous
    private String ftpPath;


    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getIdTypeAccess() {
        return idTypeAccess;
    }

    public void setIdTypeAccess(String idTypeAccess) {
        this.idTypeAccess = idTypeAccess;
    }


    public String getFtpPath() {
        return ftpPath;
    }

    public void setFtpPath(String ftpPath) {
        this.ftpPath = ftpPath;
    }

    public static String getOutputFtpPath(String server, String set){
        String ftpRequestPath = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getConfiguration().getFtpRequestPath();

        String outputDirString = ftpRequestPath
                + File.separator + FileUtil.sanitizeToValidFilename(server)
                + "-" + FileUtil.sanitizeToValidFilename(set);

        return outputDirString;
    }

    public boolean createOutputDir(String server, String ftpSet){
        File output = new File(getOutputFtpPath(server, ftpSet));
        if(output.exists()){
            try {
                FileUtils.deleteDirectory(output);
                log.info("Deleted Data Source FTP dir with success from Data Source.");

            } catch (IOException e) {
                log.error("Unable to delete Data Source FTP dir from Data Source.");
            }
        }
        return output.mkdir();
    }


    public boolean retrieveFiles(String dataSourceId){
        createOutputDir(server, dataSourceId);

        String tempRepoxFolder = getOutputFtpPath(server, dataSourceId);

        FTPClient clientFtp = new FTPClient();
        try {
            clientFtp.connect(server);
            clientFtp.login(user, password);
            clientFtp.changeWorkingDirectory(ftpPath);

            FTPFile[] ftpFiles = clientFtp.listFiles();
            // Check if FTPFile is a regular file
            getFiles(ftpFiles, clientFtp, tempRepoxFolder);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {


                clientFtp.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void getFiles(FTPFile[] ftpFiles, FTPClient clientFtp, String tempRepoxFolder){
        try {
            for (FTPFile ftpFile : ftpFiles) {
                if (ftpFile.getType() == FTPFile.FILE_TYPE) {
                    FileOutputStream fos = null;
                    File f = null;
                    try {
                        log.debug("File Name = " + ftpFile.getName());
                        f = new File(tempRepoxFolder + File.separatorChar + ftpFile.getName());
                        fos = new FileOutputStream(f);
                        if(!clientFtp.retrieveFile(ftpFile.getName(), fos))
                            throw new IOException();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    finally {
                        if (fos != null){
                            fos.close();
                        }
                    }
                    if(f != null && f.exists()){
                        f.setLastModified(ftpFile.getTimestamp().getTimeInMillis());
                    }
                }
                else if (ftpFile.getType() == FTPFile.DIRECTORY_TYPE) {
                    log.debug("Folder Name = " + ftpFile.getName());
                    clientFtp.changeWorkingDirectory(ftpFile.getName());
                    FileUtils.forceMkdir(new File(tempRepoxFolder + File.separatorChar + ftpFile.getName()));
                    getFiles(clientFtp.listFiles(), clientFtp, tempRepoxFolder + File.separatorChar + ftpFile.getName());
                    clientFtp.changeToParentDirectory();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }


    public DataSourceFtp(String server, String user, String password, String idTypeAccess, String ftpPath) {
        this.server = server;
        this.user = user;
        this.password = password;
        this.idTypeAccess = idTypeAccess;

        //if(ftpPath != null && !ftpPath.isEmpty()){
        if(ftpPath != null){
            if(ftpPath.startsWith("/"))
                this.ftpPath = ftpPath.substring(1);
            else
                this.ftpPath = ftpPath;
        }
    }

    public static void main(String[] args) {

        /*  FTPClient ftp = new FTPClient();
        FileOutputStream fos = null;

        try {
            ftp.connect("bd1.inesc-id.pt");
            // boolean login = ftp.login("ftp", "pmath2010.");
            ftp.login("ftp", "pmath2010.");

            ftp.changeWorkingDirectory("Lizbeth");

            String[] names = ftp.listNames();
            for (String name : names)
            {
                System.out.println("Name = " + name);
                File f=new File("C:/LIZBETH/" + name);
                fos = new FileOutputStream(f);
                ftp.retrieveFile (name, fos) ;

            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {

                if (fos != null) {
                    fos.close();
                }
                ftp.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }*/
    }


}


