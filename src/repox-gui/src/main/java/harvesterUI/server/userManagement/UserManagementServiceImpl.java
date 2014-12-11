package harvesterUI.server.userManagement;

import com.extjs.gxt.ui.client.data.BasePagingLoadResult;
import com.extjs.gxt.ui.client.data.PagingLoadConfig;
import com.extjs.gxt.ui.client.data.PagingLoadResult;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import harvesterUI.client.servlets.userManagement.UserManagementService;
import harvesterUI.server.RepoxServiceImpl;
import harvesterUI.server.dataManagement.RepoxDataExchangeManager;
import harvesterUI.server.ldap.LDAPAuthenticator;
import harvesterUI.server.util.Util;
import harvesterUI.shared.ServerSideException;
import harvesterUI.shared.dataTypes.DataProviderUI;
import harvesterUI.shared.dataTypes.UserAuthentication;
import harvesterUI.shared.servletResponseStates.RepoxServletResponseStates;
import harvesterUI.shared.servletResponseStates.ResponseState;
import harvesterUI.shared.users.DataProviderUser;
import harvesterUI.shared.users.User;
import harvesterUI.shared.users.UserRole;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.mindrot.jbcrypt.BCrypt;
import pt.utl.ist.repox.dataProvider.DataProvider;
import pt.utl.ist.repox.util.ConfigSingleton;
import pt.utl.ist.repox.util.PropertyUtil;
import pt.utl.ist.repox.util.RepoxContextUtilDefault;
import pt.utl.ist.repox.util.XmlUtil;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class UserManagementServiceImpl extends RemoteServiceServlet implements UserManagementService {

    private File usersFile;

    private static UserManagementServiceImpl instance = null;

    public static UserManagementServiceImpl getInstance() {
        if(instance == null) {
            instance = new UserManagementServiceImpl();
        }
        return instance;
    }

    public UserManagementServiceImpl() {
        try {
            usersFile = new File(RepoxServiceImpl.getRepoxManager().getConfiguration().getXmlConfigPath(), "users.xml");
        } catch (ServerSideException e) {
            e.printStackTrace();
        }
        if(!usersFile.exists()){
            addDefaultUser(usersFile);
        }
    }

    private void addDefaultUser(File newUsersFile){
        Document document = DocumentHelper.createDocument();
        document.addElement("users");

        Element userEl = document.getRootElement().addElement("user");
        userEl.addElement("userName").addText("admin");
        String password = "admin";
        userEl.addElement( "pass" ).addText(BCrypt.hashpw(password, BCrypt.gensalt()));
        userEl.addElement( "role" ).addText("ADMIN");
        userEl.addElement( "email" ).addText("admin@gmail.com");

        try {
            XmlUtil.writePrettyPrint(newUsersFile, document);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private HttpSession getSession() {
        return this.getThreadLocalRequest().getSession();
    }

    public PagingLoadResult<User> getPagedUsers(PagingLoadConfig config) throws ServerSideException{
        List<User> userList = getUsers();
        ArrayList<User> sublist = new ArrayList<User>();
        int start = config.getOffset();
        int limit = userList.size();
        if (config.getLimit() > 0) {
            limit = Math.min(start + config.getLimit(), limit);
        }
        for (int i = config.getOffset(); i < limit; i++) {
            sublist.add(userList.get(i));
        }
        return new BasePagingLoadResult<User>(sublist, config.getOffset(), userList.size());
    }

    public PagingLoadResult<DataProviderUI> getPagedAvailableDataProviders(PagingLoadConfig config) throws ServerSideException{
        List<DataProviderUI> userList = getAvailableDataProviders();
        ArrayList<DataProviderUI> sublist = new ArrayList<DataProviderUI>();
        int start = config.getOffset();
        int limit = userList.size();
        if (config.getLimit() > 0) {
            limit = Math.min(start + config.getLimit(), limit);
        }
        for (int i = config.getOffset(); i < limit; i++) {
            sublist.add(userList.get(i));
        }
        return new BasePagingLoadResult<DataProviderUI>(sublist, config.getOffset(), userList.size());
    }

    public List<DataProviderUI> getAvailableDataProviders(){
        List<DataProviderUI> availableDataProviders = new ArrayList<DataProviderUI>();
        try {
            for(DataProvider dataProvider : ConfigSingleton.getRepoxContextUtil().getRepoxManager().getDataManager().getDataProviders()){
                availableDataProviders.add(RepoxDataExchangeManager.parseDataProvider(dataProvider));
            }
        } catch (DocumentException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ServerSideException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return availableDataProviders;
    }

    public String validateSessionId(String sessionId) throws ServerSideException {
        try{
            if(getSession().getAttribute("sid") != null){
                String sid = getSession().getAttribute("sid").toString();
                if(sid.equals(sessionId)){
                    return (String)getSession().getAttribute("userRole");
                }
            }
            return "error";
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public UserAuthentication confirmLogin(String user, String password) throws ServerSideException {
        UserAuthentication loginData = new UserAuthentication();
        try{
            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);

            List list = document.selectNodes("//users/user");

            for(Object node: list) {
                Node n = (Node) node;
                String usr = n.valueOf( "userName" );
                String pss = n.valueOf( "pass" );
                String role = n.valueOf( "role" );
//                String language = n.valueOf( "language" );
                if(user.equals(usr) && BCrypt.checkpw(password, pss)) {
                    String sessionID = getSession().getId();
                    getSession().setAttribute("userName",usr);
                    getSession().setAttribute("sid",sessionID);
                    getSession().setAttribute("userRole",role);

                    loginData.set("sessionID",sessionID);
                    loginData.set("userName",usr);
                    loginData.set("role",role);
//                    loginData.set("language",language);
                    loginData.set("status","succeeded");
                    break;
                }
                else
                    loginData.set("status","failed");
            }
            return loginData;
        }catch (Exception e){
            loginData.set("status", "corrupt");
            return loginData;
        }
    }

    public List<User> getUsers() throws ServerSideException {
        try{
            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);

            List<User> result = new ArrayList<User>();
            List<Node> list = document.selectNodes("//users/user");

            for(Node node: list){
                String usr = node.valueOf("userName");
                String role = node.valueOf("role");
                String pass = node.valueOf("pass");
                String mail = node.valueOf("email");

                if(role.equals(UserRole.DATA_PROVIDER.name()))
                    result.add(loadDataProviderUser(node,usr,role,pass,mail));
                else
                    result.add(new User(usr,pass,role,mail));
            }
            return result;
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    private DataProviderUser loadDataProviderUser(Node userRootNode, String username, String role, String pass, String mail){
        List<String> dpIds = new ArrayList<String>();
        List<Node> list = userRootNode.selectNodes("dataProvidersAllowed/dataProvider");

        for(Node node: list)
            dpIds.add(node.valueOf("@id"));

        return new DataProviderUser(username,pass,role,mail,dpIds);
    }

    public User getUser(String userName) throws ServerSideException {
        try{
            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);

            List<Node> list = document.selectNodes("//users/user");

            for(Node node: list) {
                String usr = node.valueOf("userName");
                if(usr.equals(userName)) {
                    String role = node.valueOf("role");
                    String pass = node.valueOf("pass");
                    String mail = node.valueOf("email");
                    if(role.equals(UserRole.DATA_PROVIDER.name()))
                        return loadDataProviderUser(node,usr,role,pass,mail);
                    else
                        return new User(usr,pass,role,mail);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
        return null;
    }

    private boolean userExists(String userName) throws ServerSideException {
        try{
            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);

            List list = document.selectNodes("//users/user");

            for(Object node: list) {
                Node n = (Node) node;
                String usr = n.valueOf( "userName" );
                if(usr.toLowerCase().equals(userName.toLowerCase()))
                    return true;
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
        return false;
    }

    public ResponseState saveUser(User user, String oldUserName, boolean isUpdate) throws ServerSideException {
        try {
            if(!user.getUserName().equals(oldUserName) && userExists(user.getUserName()))
                return ResponseState.USER_ALREADY_EXISTS;

            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);
            String currentPassword = "";

            if(isUpdate){
                List<Node> list = document.selectNodes("//users/user");
                for(Node node: list) {
                    String userName = node.valueOf("userName");
                    if(userName.equals(oldUserName)) {
                        node.detach();
                        currentPassword = node.valueOf("pass");
                        break;
                    }
                }
            }

            Element userEl = document.getRootElement().addElement( "user");
            userEl.addElement("userName").addText(user.getUserName());

            if(isUpdate && user.getPassword() != null){
                userEl.addElement("pass").addText(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
                if(sendUserDataEmail(user.getUserName(),user.getMail(),"the same") == ResponseState.ERROR)
                    return ResponseState.ERROR;
            }
            else if(!isUpdate){
                String password = generateRandomPassword();
                userEl.addElement("pass").addText(BCrypt.hashpw(password, BCrypt.gensalt()));
                if(sendUserDataEmail(user.getUserName(),user.getMail(),password) == ResponseState.ERROR)
                    return ResponseState.ERROR;
            }
            else
                userEl.addElement("pass").addText(currentPassword);

            userEl.addElement("role").addText(user.getRole());
            userEl.addElement("email").addText(user.getMail());

            if(user instanceof DataProviderUser){
                Element dpIds = userEl.addElement("dataProvidersAllowed");
                for(String dpId : ((DataProviderUser) user).getAllowedDataProviderIds())
                    dpIds.addElement("dataProvider").addAttribute("id",dpId);
            }

            XmlUtil.writePrettyPrint(usersFile, document);
            return ResponseState.SUCCESS;
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public ResponseState addDPtoUser(String username, String dpId) {
        if(username != null){
            try {
                SAXReader reader = new SAXReader();
                Document document = reader.read(usersFile);

                List<Node> list = document.selectNodes("//users/user");
                for(Node node: list) {
                    String currentUsername = node.valueOf("userName");
                    if(currentUsername.equals(username)) {
                        Element dpsEl = (Element)node.selectSingleNode("dataProvidersAllowed");
                        dpsEl.addElement("dataProvider").addAttribute("id",dpId);
                        break;
                    }
                }

                XmlUtil.writePrettyPrint(usersFile, document);
                return ResponseState.SUCCESS;
            }catch (Exception e){
                e.printStackTrace();
                return ResponseState.ERROR;
            }
        }
        else
            return ResponseState.SUCCESS;
    }

    public ResponseState removeDPFromUsers(String dpId) {
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);

            List<Node> list = document.selectNodes("//users/user");
            for(Node node: list) {
                String currentUserRole = node.valueOf("role");
                if(currentUserRole.equals("DATA_PROVIDER")) {
                    List<Node> dpList = node.selectNodes("dataProvidersAllowed/dataProvider");
                    for(Node dpNode: dpList) {
                        String currentDpId = dpNode.valueOf("@id");
                        if(currentDpId.equals(dpId))
                            dpNode.detach();
                    }
                }
            }

            XmlUtil.writePrettyPrint(usersFile, document);
            return ResponseState.SUCCESS;
        }catch (Exception e){
            e.printStackTrace();
            return ResponseState.ERROR;
        }
    }

    public void removeUsers(List<User> users) throws ServerSideException {
        try{
            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);
            for(User user : users){
                List<Node> list = document.selectNodes("//users/user");

                for(Node node: list){
                    String userName = node.valueOf("userName");
                    if(userName.equals(user.getUserName()))
                        node.detach();
                }
            }

            XmlUtil.writePrettyPrint(usersFile, document);
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public ResponseState resetUserPassword(String userName) throws ServerSideException {
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);

            List list = document.selectNodes("//users/user");
            boolean foundMatch = false;
            ResponseState result = ResponseState.ERROR;

            for(Object node: list) {
                Node n = (Node) node;
                String usr = n.valueOf("userName");
                String mail = n.valueOf("email");
                if(usr.equals(userName)) {
                    String resetPassword = generateRandomPassword();
                    n.selectSingleNode("pass").setText(BCrypt.hashpw(resetPassword, BCrypt.gensalt()));
                    foundMatch = true;
                    result = sendUserDataEmail(userName,mail,resetPassword);
                }
            }

            if(foundMatch && result == ResponseState.SUCCESS) {
                XmlUtil.writePrettyPrint(usersFile, document);
            }
            return result;
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

//    private String encryptPassword(String pwd) {
//        MessageDigest mdEnc = null; // Encryption algorithm
//        try {
//            mdEnc = MessageDigest.getInstance("MD5");
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }
//        mdEnc.reset();
//        mdEnc.update(pwd.getBytes(), 0, pwd.length());
//        return new BigInteger(1, mdEnc.digest()).toString(16); // Encrypted string
//    }

    private static String generateRandomPassword(){
        return new BigInteger(130, new SecureRandom()).toString(32);
    }

    public String sendFeedbackEmail(String userEmail, String title, String message, String messageType) throws ServerSideException {
        return RepoxServiceImpl.getProjectManager().sendFeedbackEmail(userEmail, title, message, messageType);
    }

    public String savePerPageData(String username, int dataPerPage) throws ServerSideException {
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(usersFile);
            List list = document.selectNodes("//users/user");

            for(Object node: list) {
                Node n = (Node) node;
                String userName = n.valueOf("userName");
                if(userName.equals(username)) {
                    n.selectSingleNode("pageSize").setText(String.valueOf(dataPerPage));
                }
            }

            XmlUtil.writePrettyPrint(usersFile, document);
            return "SUCCESS";
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

//    public String saveLanguageData(String username, String language) throws ServerSideException {
//        try {
//            SAXReader reader = new SAXReader();
//            Document document = reader.read(this.getThreadLocalRequest().usersFile);
//            List list = document.selectNodes("//users/user");
//
//            for(Object node: list) {
//                Node n = (Node) node;
//                String userName = n.valueOf("userName");
//                if(userName.equals(username)) {
//                    n.selectSingleNode("language").setText(language);
//                }
//            }
//
//            File usersFile = new File(this.getThreadLocalRequest().usersFile);
//            XmlUtil.writePrettyPrint(usersFile, document);
//            return "SUCCESS";
//        }catch (Exception e){
//            e.printStackTrace();
//            throw new ServerSideException(Util.stackTraceToString(e));
//        }
//    }

    public void addUserActivityData(String serverUrl){
        try{
            String email = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getConfiguration().getAdministratorEmail();
            SAXReader reader = new SAXReader();
            String addRepoxUserActivityData = "http://repox.ist.utl.pt/repoxManagementServices/rest/addUserActivityData?email="+email+"&url="+serverUrl;
            reader.read(new URL(addRepoxUserActivityData));
        } catch (MalformedURLException e) {
//            e.printStackTrace();
        } catch (DocumentException e) {
            // When no internet connection available don't complain

        } catch (Exception e){
            // Do nothing on any chance
        }
    }

    public RepoxServletResponseStates.GeneralStates registerNewEntity(String name, String mail, String institution, String skypeContact,String repoxUrl) throws ServerSideException {
        try {
            Properties properties = PropertyUtil.loadCorrectedConfiguration(RepoxContextUtilDefault.CONFIG_FILE);
            properties.setProperty("firstTimeUser","false");
            PropertyUtil.saveProperties(properties, RepoxContextUtilDefault.CONFIG_FILE);

            SAXReader reader = new SAXReader();
            String createRepoxUser = "http://repox.ist.utl.pt/repoxManagementServices/rest/createRegistration?name="+name+
                    "&email="+mail+"&institution="+institution+
                    "&skypeContact="+(skypeContact == null ? "" : skypeContact)+
                    "&repoxUrl="+(repoxUrl == null ? "" : repoxUrl);
            reader.read(new URL(createRepoxUser));
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return RepoxServletResponseStates.GeneralStates.ERROR;
        } catch (DocumentException e) {
//            e.printStackTrace();
            // When no internet connection available
            saveEntityRegistration(name, mail, institution, skypeContact, repoxUrl);
            return RepoxServletResponseStates.GeneralStates.NO_INTERNET_CONNECTION;
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }

        return RepoxServletResponseStates.GeneralStates.SUCCESS;
    }

    private void saveEntityRegistration(String name, String mail, String institution, String skypeContact,String repoxUrl) throws ServerSideException {
        try{
            File registrationFile = new File(ConfigSingleton.getRepoxContextUtil().getRepoxManager().
                    getConfiguration().getXmlConfigPath() + File.separator + "registrationInfo.xml");

            if(!registrationFile.exists()){
                Document document = DocumentHelper.createDocument();

                Element registrationNode = document.addElement("registrationInfo");
                registrationNode.addAttribute("name",name);
                registrationNode.addAttribute("mail",mail);
                registrationNode.addAttribute("institution",institution);
                registrationNode.addAttribute("skypeContact",skypeContact);
                registrationNode.addAttribute("repoxUrl",repoxUrl);
                registrationNode.addAttribute("registryDate",new Date().toString());

                XmlUtil.writePrettyPrint(registrationFile, document);
            }
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public boolean isFirstTimeRepoxUsed() throws ServerSideException {
        try{
            Properties properties = PropertyUtil.loadCorrectedConfiguration(RepoxContextUtilDefault.CONFIG_FILE);
            boolean isFirstTime = Boolean.valueOf(properties.getProperty("firstTimeUser"));
            if(!isFirstTime){
                trySendRegistrationFromXML();
            }
            return isFirstTime;
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    private String trySendRegistrationFromXML() throws ServerSideException {
        try{
            File registrationFile = new File(ConfigSingleton.getRepoxContextUtil().getRepoxManager().
                    getConfiguration().getXmlConfigPath() + File.separator + "registrationInfo.xml");

            if(registrationFile.exists()){
                SAXReader reader = new SAXReader();
                Document document = reader.read(registrationFile);

                String name = document.valueOf("//registrationInfo/@name");
                String mail = document.valueOf("//registrationInfo/@mail");
                String institution = document.valueOf("//registrationInfo/@institution");
                String skypeContact = document.valueOf("//registrationInfo/@skypeContact");
                String repoxUrl = document.valueOf("//registrationInfo/@repoxUrl");

                RepoxServletResponseStates.GeneralStates state =  registerNewEntity(name, mail, institution, skypeContact,repoxUrl);
                if(state == RepoxServletResponseStates.GeneralStates.SUCCESS)
                    registrationFile.delete();
            }
            return "OK";
        }catch (NullPointerException e){
//            e.printStackTrace();
            return "OK";
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    private ResponseState sendUserDataEmail(String username, String email, String password) throws ServerSideException {
        return RepoxServiceImpl.getProjectManager().sendUserDataEmail(username, email, password);
    }

    public boolean checkLDAPAuthentication(String username, String password) throws ServerSideException{
        String ldapHost = RepoxServiceImpl.getRepoxManager().getConfiguration().getLdapHost();
        String ldapUSerPrefix = RepoxServiceImpl.getRepoxManager().getConfiguration().getLdapUserPrefix();
        String ldapLoginDN = RepoxServiceImpl.getRepoxManager().getConfiguration().getLdapLoginDN();
        String loginDN = ldapUSerPrefix + username + ldapLoginDN;
        return LDAPAuthenticator.checkLDAPAuthentication(ldapHost,loginDN,password);
    }
}
