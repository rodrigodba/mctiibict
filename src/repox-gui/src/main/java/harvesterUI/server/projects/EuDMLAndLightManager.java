package harvesterUI.server.projects;

import com.extjs.gxt.ui.client.data.ModelData;
import harvesterUI.server.RepoxServiceImpl;
import harvesterUI.server.dataManagement.DataType;
import harvesterUI.server.dataManagement.RepoxDataExchangeManager;
import harvesterUI.server.dataManagement.dataSets.DataSetOperationsServiceImpl;
import harvesterUI.server.dataManagement.filters.FilterManagementUtil;
import harvesterUI.server.dataManagement.filters.FilteredDataResponse;
import harvesterUI.server.userManagement.UserManagementServiceImpl;
import harvesterUI.server.util.PagingUtil;
import harvesterUI.server.util.StatisticsUtil;
import harvesterUI.server.util.Util;
import harvesterUI.shared.ServerSideException;
import harvesterUI.shared.dataTypes.AggregatorUI;
import harvesterUI.shared.dataTypes.DataContainer;
import harvesterUI.shared.dataTypes.DataProviderUI;
import harvesterUI.shared.dataTypes.SaveDataResponse;
import harvesterUI.shared.dataTypes.admin.AdminInfo;
import harvesterUI.shared.dataTypes.dataSet.DataSourceUI;
import harvesterUI.shared.dataTypes.dataSet.DatasetType;
import harvesterUI.shared.filters.FilterAttribute;
import harvesterUI.shared.filters.FilterQuery;
import harvesterUI.shared.filters.FilterType;
import harvesterUI.shared.search.BaseSearchResult;
import harvesterUI.shared.servletResponseStates.ResponseState;
import harvesterUI.shared.statistics.RepoxStatisticsUI;
import harvesterUI.shared.statistics.StatisticsType;
import harvesterUI.shared.tasks.OldTaskUI;
import harvesterUI.shared.users.DataProviderUser;
import harvesterUI.shared.users.User;
import org.dom4j.DocumentException;
import pt.utl.ist.repox.RepoxConfiguration;
import pt.utl.ist.repox.dataProvider.*;
import pt.utl.ist.repox.dataProvider.dataSource.IdExtracted;
import pt.utl.ist.repox.metadataTransformation.MetadataTransformation;
import pt.utl.ist.repox.statistics.RepoxStatisticsDefault;
import pt.utl.ist.repox.statistics.StatisticsManagerDefault;
import pt.utl.ist.repox.task.OldTask;
import pt.utl.ist.repox.task.oldTasks.OldTaskReviewer;
import pt.utl.ist.repox.util.ConfigSingleton;
import pt.utl.ist.repox.util.PropertyUtil;
import pt.utl.ist.repox.util.RepoxContextUtilDefault;

import javax.mail.AuthenticationFailedException;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

/**
 * Created to Project REPOX
 * User: Edmundo
 * Date: 30-04-2012
 * Time: 11:29
 */
public class EuDMLAndLightManager extends ProjectManager {

    private int filteredDataSize = 0;

    public EuDMLAndLightManager() {
        ConfigSingleton.setRepoxContextUtil(new RepoxContextUtilDefault());
    }

    /*
     * Same functions as LIGHT
     */

    public RepoxStatisticsUI getStatisticsInfo(StatisticsType statisticsType, String username) throws ServerSideException {
        try {
            StatisticsManagerDefault manager = (StatisticsManagerDefault) ConfigSingleton.getRepoxContextUtil().
                    getRepoxManager().getStatisticsManager();
            User user = UserManagementServiceImpl.getInstance().getUser(username);
            List<String> dpIds;
            if(user instanceof DataProviderUser)
                dpIds = ((DataProviderUser) user).getAllowedDataProviderIds();
            else
                dpIds = null;

            RepoxStatisticsDefault statistics = (RepoxStatisticsDefault)manager.generateStatistics(dpIds);

            NumberFormat numberFormat = NumberFormat.getInstance(Locale.GERMAN);
            String totalRecords = numberFormat.format(statistics.getRecordsTotal());

            int recordsAvgDataSource = (int)statistics.getRecordsAvgDataSource();
            int recordsAvgDataProvider = (int)statistics.getRecordsAvgDataProvider();

            return new RepoxStatisticsUI(statistics.getGenerationDate(),statistics.getDataSourcesIdExtracted(),
                    statistics.getDataSourcesIdGenerated(),statistics.getDataSourcesIdProvided(),
                    statistics.getDataProviders(),
                    statistics.getDataSourcesOai() + statistics.getDataSourcesZ3950() + statistics.getDataSourcesDirectoryImporter(),
                    statistics.getDataSourcesOai(), statistics.getDataSourcesZ3950(),
                    statistics.getDataSourcesDirectoryImporter(),
                    StatisticsUtil.getMetadataFormatStatistics(statistics.getDataSourcesMetadataFormats(),false),
                    StatisticsUtil.getMetadataFormatStatistics(statistics.getDataSourcesMetadataFormats(),true),
                    recordsAvgDataSource,recordsAvgDataProvider,
                    statistics.getCountriesRecords(),totalRecords);
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public Map<String,String> getFullCountryList() throws ServerSideException{
        try{
            return Countries.getCountries();
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public AdminInfo loadAdminFormInfo() throws ServerSideException{
        try{
            RepoxConfiguration configuration = RepoxServiceImpl.getRepoxManager().getConfiguration();
            AdminInfo adminInfo = new AdminInfo();
            adminInfo.set("repositoryFolder",configuration.getRepositoryPath());
            adminInfo.set("configFilesFolder",configuration.getXmlConfigPath());
            adminInfo.set("oaiRequestFolder", configuration.getOaiRequestPath());
            adminInfo.set("derbyDbFolder",configuration.getDatabasePath());
            adminInfo.set("baseUrn",configuration.getBaseUrn());

            Properties properties = PropertyUtil.loadCorrectedConfiguration("oaicat.properties");
            adminInfo.set("oaiRepoName",properties.getProperty("Identify.repositoryName","undefined"));
            adminInfo.set("oaiMaxList",properties.getProperty("DataSourceOAICatalog.maxListSize","undefined"));

//            adminInfo.set("defaultExportFolder",configuration.getRepositoryPath());
            adminInfo.set("adminEmail",configuration.getAdministratorEmail());
            adminInfo.set("smtpServer",configuration.getSmtpServer());
            adminInfo.set("smtpPort",configuration.getSmtpPort());
            adminInfo.set("repoxDefualtEmailSender",configuration.getDefaultEmail());
            adminInfo.set("httpRequestFolder",configuration.getHttpRequestPath());
            adminInfo.set("ftpRequestFolder",configuration.getFtpRequestPath());
            adminInfo.set("sampleRecords",configuration.getSampleRecords());
            adminInfo.set("useCountriesTxt",configuration.getUseCountriesTxt());
            adminInfo.set("sendEmailAfterIngest",configuration.getSendEmailAfterIngest());
            adminInfo.set("useMailSSLAuthentication",configuration.isUseMailSSLAuthentication());
            adminInfo.set("useOAINamespace",configuration.isUseOAINamespace());

            // optional fields
            if(configuration.getMailPassword() != null)
                adminInfo.set("adminPass",configuration.getMailPassword());
            if(configuration.getLdapHost() != null)
                adminInfo.set("ldapHost",configuration.getLdapHost());
            if(configuration.getLdapUserPrefix() != null)
                adminInfo.set("ldapUserPrefix",configuration.getLdapUserPrefix());
            if(configuration.getLdapLoginDN() != null)
                adminInfo.set("ldapLoginDN",configuration.getLdapLoginDN());

            return adminInfo;
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public void saveAdminFormInfo(AdminInfo results) throws ServerSideException{
        try{
            Properties properties = PropertyUtil.loadCorrectedConfiguration(RepoxContextUtilDefault.CONFIG_FILE);
            properties.setProperty("repository.dir",(String)results.get("repositoryFolder"));
            properties.setProperty("xmlConfig.dir",(String)results.get("configFilesFolder"));
            properties.setProperty("oairequests.dir",(String)results.get("oaiRequestFolder"));
            properties.setProperty("database.dir",(String)results.get("derbyDbFolder"));
            properties.setProperty("baseurn",(String)results.get("baseUrn"));
            properties.setProperty("administrator.email",(String)results.get("adminEmail"));
            properties.setProperty("smtp.server",(String)results.get("smtpServer"));
            properties.setProperty("smtp.port",(String)results.get("smtpPort"));
            properties.setProperty("default.email",(String)results.get("repoxDefaultEmailSender"));
            properties.setProperty("httprequests.dir",(String)results.get("httpRequestFolder"));
            properties.setProperty("ftprequests.dir",(String)results.get("ftpRequestFolder"));
            properties.setProperty("sample.records",(String)results.get("sampleRecords"));
            properties.setProperty("userCountriesTxtFile",String.valueOf(results.get("useCountriesTxt")));
            properties.setProperty("sendEmailAfterIngest",String.valueOf(results.get("sendEmailAfterIngest")));
            properties.setProperty("useMailSSLAuthentication",String.valueOf(results.get("useMailSSLAuthentication")));
            properties.setProperty("useOAINamespace",String.valueOf(results.get("useOAINamespace")));

            // optional fields
            if(results.get("adminPass") != null)
                properties.setProperty("administrator.email.pass",(String)results.get("adminPass"));
            if(results.get("ldapHost") != null)
                properties.setProperty("ldapHost",(String)results.get("ldapHost"));
            if(results.get("ldapUserPrefix") != null)
                properties.setProperty("ldapUserPrefix",(String)results.get("ldapUserPrefix"));
            if(results.get("ldapLoginDN") != null)
                properties.setProperty("ldapLoginDN",(String)results.get("ldapLoginDN"));

            Properties oaiProperties = PropertyUtil.loadCorrectedConfiguration("oaicat.properties");
            if(results.get("oaiRepoName") != null)
                oaiProperties.setProperty("Identify.repositoryName",(String)results.get("oaiRepoName"));
            if(results.get("oaiMaxList") != null)
                oaiProperties.setProperty("DataSourceOAICatalog.maxListSize",(String)results.get("oaiMaxList"));

            PropertyUtil.saveProperties(oaiProperties, "oaicat.properties");
            reloadOAIProperties(results.getReloadOAIPropertiesUrl());
            PropertyUtil.saveProperties(properties, RepoxContextUtilDefault.CONFIG_FILE);
            ConfigSingleton.getRepoxContextUtil().reloadProperties();
//            System.out.println("Done save admin");
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    /*********************************************************
     Paging Light and Eudml Functions
     **********************************************************/

    public List<DataContainer> getParsedData(int offSet, int limit) throws ServerSideException{
        List<DataContainer> mainData = new ArrayList<DataContainer>();
        try{
            List<Object> allDataList = RepoxServiceImpl.getRepoxManager().getDataManager().getAllDataList();
            int realLimit = allDataList.size();
            if(realLimit > 0){
                DataProviderUI currentDataProvider = null;
                for (int i = offSet; i<(limit>realLimit ? realLimit : limit); i++){
                    if(allDataList.get(i) instanceof DataProvider){
                        currentDataProvider = RepoxDataExchangeManager.parseDataProvider((DataProvider) allDataList.get(i));
                        mainData.add(currentDataProvider);
                    } else if(allDataList.get(i) instanceof DataSourceContainer){
                        if(currentDataProvider == null){
                            currentDataProvider = RepoxDataExchangeManager.parseDataProvider(RepoxServiceImpl.getRepoxManager().getDataManager().
                                    getDataProviderParent(((DataSourceContainer) allDataList.get(i)).getDataSource().getId()));
                            mainData.add(currentDataProvider);
                        }
                        DataSourceUI dataSourceUI = parseDataSource((DataSourceContainer) allDataList.get(i), currentDataProvider);
                        currentDataProvider.add(dataSourceUI);
                        currentDataProvider.addDataSource(dataSourceUI);
                    }
                }
            }
            return mainData;
        } catch (IndexOutOfBoundsException e){
            throw new ServerSideException(Util.stackTraceToString(e));
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public DataContainer getSearchResult(ModelData data) throws ServerSideException{
        try{
            DataContainer dataContainer = new DataContainer(UUID.randomUUID().toString());
            String id = data.get("id");

            if(data.get("dataType").equals(DataType.DATA_PROVIDER.name())){
                DataProvider dataProvider = RepoxServiceImpl.getRepoxManager().getDataManager().getDataProvider(id);
                DataProviderUI dataProviderUI = RepoxDataExchangeManager.parseDataProvider(dataProvider);
                dataContainer.add(dataProviderUI);

                for(DataSourceContainer dataSourceContainer : dataProvider.getDataSourceContainers().values()){
                    DataSourceUI dataSourceUI = parseDataSource(dataSourceContainer, dataProviderUI);
                    dataProviderUI.add(dataSourceUI);
                    dataProviderUI.addDataSource(dataSourceUI);
                }
            }else if(data.get("dataType").equals(DataType.DATA_SET.name())){
                DataProviderUI dataProviderUI = RepoxDataExchangeManager.parseDataProvider(RepoxServiceImpl.getRepoxManager().getDataManager().
                        getDataProviderParent(id));
                dataContainer.add(dataProviderUI);

                DataSourceContainer dataSourceContainer = RepoxServiceImpl.getRepoxManager().getDataManager().getDataSourceContainer(id);
                DataSourceUI dataSourceUI = parseDataSource(dataSourceContainer, dataProviderUI);
                dataProviderUI.add(dataSourceUI);
                dataProviderUI.addDataSource(dataSourceUI);
            }
            return dataContainer;
        } catch (Exception e) {
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    private DataSourceContainer getFirstHashElement(HashMap<String,DataSourceContainer> containerHashMap){
        for(DataSourceContainer dataSourceContainer : containerHashMap.values()){
            return dataSourceContainer;
        }
        return null;
    }

    public List<DataContainer> getViewResult(int offset, int limit, String type) throws ServerSideException{
        List<DataContainer> mainData = new ArrayList<DataContainer>();
        try{
            if(type.equals("DATA_PROVIDERS")){
                List<DataProvider> dpList = RepoxServiceImpl.getRepoxManager().getDataManager().getDataProviders();
                for (int i = offset; i<limit && i<dpList.size(); i++){
                    mainData.add(RepoxDataExchangeManager.parseDataProvider(dpList.get(i)));
                }
            }else if(type.equals("DATA_SETS")){
                for(DataProvider dataProvider : RepoxServiceImpl.getRepoxManager().getDataManager().getDataProviders()){
                    if(dataProvider.getDataSourceContainers() != null) {
                        for (DataSourceContainer dataSourceContainer : dataProvider.getDataSourceContainers().values()) {
                            DataProviderUI currentDataProvider = RepoxDataExchangeManager.parseDataProvider(RepoxServiceImpl.getRepoxManager().getDataManager().
                                    getDataProviderParent(dataSourceContainer.getDataSource().getId()));
                            mainData.add(parseDataSource(dataSourceContainer, currentDataProvider));
                        }
                    }
                }
            }
            return mainData;
        }catch (Exception e) {
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public List<FilterAttribute> getDPAttributes(FilterType filterType,List<FilterQuery> filterQueries) throws ServerSideException {
        Map<String,String> countryMap = Countries.getCountries();

        List<FilterAttribute> values = new ArrayList<FilterAttribute>();
        List<Object> allDataList = FilterManagementUtil.getInstance().getRawFilteredData(filterQueries).getFilteredData();
        for(Object object : allDataList){
            if(object instanceof DataProvider){
                DataProvider dataProvider = (DataProvider)object;
                if(filterType.equals(FilterType.COUNTRY)){
                    String showName = "<img src=\"resources/images/countries/" +
                            dataProvider.getCountry() + ".png\" alt=\"" + countryMap.get(dataProvider.getCountry()) + "\" title=\"" +
                            countryMap.get(dataProvider.getCountry()) + "\"/> " + countryMap.get(dataProvider.getCountry());
                    values.add(new FilterAttribute(showName,dataProvider.getCountry()));
                }else if(filterType.equals(FilterType.DP_TYPE)){

                }
            }else if(object instanceof DataSourceContainer){
                DataProvider parent = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getDataManager().
                        getDataProviderParent(((DataSourceContainer) object).getDataSource().getId());
                if(filterType.equals(FilterType.COUNTRY)){
                    String showName = "<img src=\"resources/images/countries/" +
                            parent.getCountry() + ".png\" alt=\"" + countryMap.get(parent.getCountry()) + "\" title=\"" +
                            countryMap.get(parent.getCountry()) + "\"/> " + countryMap.get(parent.getCountry());
                    values.add(new FilterAttribute(showName,parent.getCountry()));
                }
            }
        }
        return values;
    }

    public DataContainer getFilteredData(List<FilterQuery> filterQueries,int offset, int limit)throws ServerSideException{
        try{
            FilteredDataResponse filteredDataResponse = FilterManagementUtil.getInstance().getRawFilteredData(filterQueries);
            List<DataContainer> resultData;
            if(filteredDataResponse.isDataWasFiltered()){
                resultData = getFilteredDataTreeResult(offset, limit, filteredDataResponse.getFilteredData());
                filteredDataSize = filteredDataResponse.getFilteredData().size();
            }else
                resultData = getParsedData(offset,limit);

            DataContainer dataContainer = new DataContainer(UUID.randomUUID().toString());
            for(DataContainer model : resultData)
                dataContainer.add(model);
            return dataContainer;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public int getFilteredDataSize(){
        return filteredDataSize;
    }

    private List<DataContainer> getFilteredDataTreeResult(int offSet, int limit, List<Object> filteredDataList) throws ServerSideException{
        List<DataContainer> mainData = new ArrayList<DataContainer>();
        try{
            int filteredListSize = filteredDataList.size();
            if(filteredListSize > 0){
                DataProviderUI currentDataProvider = null;
                int realLimit = (limit>filteredListSize ? filteredListSize : limit);
                for (int i = offSet; i<realLimit; i++){
                    if(filteredDataList.get(i) instanceof DataProvider){
                        currentDataProvider = RepoxDataExchangeManager.parseDataProvider(((DataProvider) filteredDataList.get(i)));
                        mainData.add(currentDataProvider);
                    } else if(filteredDataList.get(i) instanceof DataSourceContainer){
                        if(currentDataProvider == null || isDifferentDataProvider(currentDataProvider,(DataSourceContainer)filteredDataList.get(i))){
                            currentDataProvider = RepoxDataExchangeManager.parseDataProvider(RepoxServiceImpl.getRepoxManager().getDataManager().
                                    getDataProviderParent(((DataSourceContainer) filteredDataList.get(i)).getDataSource().getId()));
                            mainData.add(currentDataProvider);
                            if(limit <= filteredDataList.size())
                                realLimit--;
                        }

                        DataSourceUI dataSourceUI = parseDataSource((DataSourceContainer) filteredDataList.get(i), currentDataProvider);
                        currentDataProvider.add(dataSourceUI);
                        currentDataProvider.addDataSource(dataSourceUI);
                    }
                }
            }
            return mainData;
        } catch (IndexOutOfBoundsException e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    /*********************************************************
     Save Light Functions
     **********************************************************/

    public SaveDataResponse saveDataProvider(boolean update, DataProviderUI dataProviderUI, int pageSize, String username) throws ServerSideException{return null;}
    public String deleteDataProviders(List<DataProviderUI> dataProviderUIs) throws ServerSideException{return null;}
    public SaveDataResponse moveDataProvider(List<DataProviderUI> dataProviders, ModelData aggregatorUI, int pageSize) throws ServerSideException{return null;}

    public SaveDataResponse saveDataSource(boolean update, DatasetType type, String originalDSset, DataSourceUI dataSourceUI, int pageSize) throws ServerSideException {return null;}
    public String addAllOAIURL(String url,String dataProviderID,String dsSchema,String dsNamespace,
                               String dsMTDFormat, String name, String nameCode, String exportPath,DataSetOperationsServiceImpl dataSetOperationsService) throws ServerSideException{return null;}
    public String deleteDataSources(List<DataSourceUI> dataSourceUIs) throws ServerSideException{return null;}

    public SaveDataResponse moveDataSources(List<DataSourceUI> dataSourceUIs, ModelData dataProviderUI, int pageSize) throws ServerSideException{
        SaveDataResponse saveDataResponse = new SaveDataResponse();
        try {
            DataManager dataManager = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getDataManager();
            for(DataSourceUI dataSourceUI : dataSourceUIs) {
                dataManager.moveDataSource((String)dataProviderUI.get("id"), dataSourceUI.getDataSourceSet());
            }
            // Jump to the page of the FIRST data source moved on the list
            saveDataResponse.setPage(PagingUtil.getDataPage(dataSourceUIs.get(0).getDataSourceSet(), pageSize));
            saveDataResponse.setResponseState(ResponseState.SUCCESS);
            return saveDataResponse;
        } catch (IOException e) {
            saveDataResponse.setResponseState(ResponseState.ERROR);
        } catch (DocumentException e) {
            saveDataResponse.setResponseState(ResponseState.ERROR);
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
        return saveDataResponse;
    }

    public Boolean dataSourceExport(DataSourceUI dataSourceUI) throws ServerSideException{return null;}

    public List<OldTaskUI> getParsedOldTasks(List<FilterQuery> filterQueries) throws ServerSideException{
        try{
            List<OldTaskUI> oldTaskUIs = new ArrayList<OldTaskUI>();

            List<Object> allData = FilterManagementUtil.getInstance().getRawFilteredData(filterQueries).getFilteredData();
            for(Object model : allData){
                if(model instanceof DataProvider){
                    for(DataSourceContainer dataSourceContainer : ((DataProvider)model).getDataSourceContainers().values()){
                        for(OldTask oldTask: dataSourceContainer.getDataSource().getOldTasksList()) {
                            OldTaskUI oldTaskUI = new OldTaskUI(dataSourceContainer.getDataSource().getId(),oldTask.getId(),oldTask.getLogName(),
                                    oldTask.getIngestType(),oldTask.getStatus(),oldTask.getRetries(),
                                    oldTask.getRetryMax(),oldTask.getDelay(),oldTask.getDateString(),oldTask.getRecords());
                            oldTaskUIs.add(oldTaskUI);
                        }
                    }
                }
            }
            return oldTaskUIs;
        } catch (Exception e){
            throw  new ServerSideException(Util.stackTraceToString(e));
        }
    }

    private DataSourceUI parseDataSource(DataSourceContainer dataSourceContainer,DataProviderUI dataProviderUI) throws ServerSideException{
        DataSource dataSource = dataSourceContainer.getDataSource();
        new OldTaskReviewer().addNotListedOldTasks(dataSource.getId());
        String metadataFormat = dataSource.getMetadataFormat();
        String oaiSchemas = metadataFormat;
        if(dataSource.getMetadataTransformations() != null) {
            for(MetadataTransformation metadataTransformation : dataSource.getMetadataTransformations().values()){
                oaiSchemas += " | " + metadataTransformation.getDestinationFormat();
            }
        }

        String recordPolicy;
        if(dataSource.getRecordIdPolicy() instanceof IdExtracted)
            recordPolicy = "IdExtracted";
        else
            recordPolicy = "IdGenerated";

        DataSourceUI newDataSourceUI = new DataSourceUI(dataProviderUI,dataSource.getDescription(),
                dataSource.getId(),oaiSchemas,"TODO","",
                dataSource.getDescription(),"","","","",
                recordPolicy,dataSource.getMetadataFormat());

        newDataSourceUI.setSchema(dataSource.getSchema());
        newDataSourceUI.setMetadataNamespace(dataSource.getNamespace());
        newDataSourceUI.setIsSample(dataSource.isSample());

        // External Services Run Type
        if(dataSource.getExternalServicesRunType() != null)
            newDataSourceUI.setExternalServicesRunType(dataSource.getExternalServicesRunType().name());

        newDataSourceUI.setType(dataProviderUI.getType());

        newDataSourceUI.setExportDirectory(dataSource.getExportDir() != null ? dataSource.getExportDir().getAbsolutePath() : "");

        String marcFormat = dataSource.getMarcFormat();
        if(marcFormat != null && !marcFormat.isEmpty())
            newDataSourceUI.setMarcFormat(marcFormat);

        RepoxDataExchangeManager.parseDataSourceSubType(newDataSourceUI, dataSource);
        RepoxDataExchangeManager.getOldTasks(dataSource, newDataSourceUI);
        RepoxDataExchangeManager.getScheduledTasks(newDataSourceUI);

        RepoxDataExchangeManager.getMetadataTransformations(dataSource, newDataSourceUI);
        RepoxDataExchangeManager.getDataSetInfo(dataSource, newDataSourceUI);
        RepoxDataExchangeManager.getExternalServices(dataSource, newDataSourceUI);
        RepoxDataExchangeManager.getTags(dataSource, newDataSourceUI);
        return newDataSourceUI;
    }

    public String sendFeedbackEmail(String userEmail, String title, String message, String messageType) throws ServerSideException {
        try {
//            String fromEmail = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getConfiguration().getDefaultEmail();
            String developTeamMail = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getConfiguration().getDefaultEmail();
            String[] recipientsEmail = new String[]{developTeamMail};
            String messageTitle = "[" + messageType + "] - " + title + " - Sent by user: " + userEmail;
            File[] attachments = null;
            ConfigSingleton.getRepoxContextUtil().getRepoxManager().getEmailClient().sendEmail(developTeamMail,
                    recipientsEmail, messageTitle, message, attachments, null);
            return "SUCCESS";
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public ResponseState sendUserDataEmail(String username, String email, String password) throws ServerSideException {
        try {
            // Tel
            String smtpServer = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getConfiguration().getSmtpServer();
            if(smtpServer == null || smtpServer.isEmpty()) {
                return ResponseState.ERROR;
            }

            String fromEmail = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getConfiguration().getDefaultEmail();
            String subject = "REPOX User Account Data";
            String[] recipientsEmail = new String[]{email};
            File[] attachments = null;

            String message = "Your user name is " + username
                    + "\nYour REPOX password is " + password
                    + "\nAfter you login into REPOX you can change your password in the Edit Account menu."
                    + "\n\n--------------------------------------------------------------------------------\n"
                    + "This email is sent automatically by REPOX. Do not reply to this message.";

            ConfigSingleton.getRepoxContextUtil().getRepoxManager().getEmailClient().sendEmail(fromEmail, recipientsEmail, subject, message, attachments, null);

            return ResponseState.SUCCESS;
        }catch (AuthenticationFailedException e){
            return ResponseState.EMAIL_AUTHENTICATION_ERROR;
        }catch (Exception e){
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public boolean isCorrectAggregator(String dataSetId, String aggregatorId) throws ServerSideException{
        return false;
    }

    public DataSourceUI getDataSetInfo(String dataSetId) throws ServerSideException{
        try{
            DataProvider dataProvider = RepoxServiceImpl.getRepoxManager().getDataManager().
                    getDataProviderParent(dataSetId);
            if(dataProvider == null)
                return null;
            DataProviderUI currentDataProvider = RepoxDataExchangeManager.parseDataProvider(dataProvider);
            DataSourceContainer container = RepoxServiceImpl.getRepoxManager().getDataManager().getDataSourceContainer(dataSetId);
            if(container == null)
                return null;
            return parseDataSource(container,currentDataProvider);
        } catch (Exception e) {
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public List<BaseSearchResult> getMainGridSearchResults(String searchValue, List<FilterQuery> filterQueries) throws ServerSideException{
        List<BaseSearchResult> searchData = new ArrayList<BaseSearchResult>();
        try{
            List<Object> allDataList = FilterManagementUtil.getInstance().getRawFilteredData(filterQueries).getFilteredData();
            for (Object data : allDataList){
                if(data instanceof DataProvider){
                    String id = ((DataProvider) data).getId();
                    String name = ((DataProvider) data).getName();
                    String description = ((DataProvider) data).getDescription();
                    if(Util.compareStrings(searchValue, description) ||
                            Util.compareStrings(searchValue, name)){
                        BaseSearchResult dp = createModelLight(id, name, description, "", DataType.DATA_PROVIDER);
                        searchData.add(dp);
                    }
                }else if(data instanceof DataSourceContainer){
                    String id = ((DataSourceContainer) data).getDataSource().getId();
                    String description = ((DataSourceContainer) data).getDataSource().getDescription();
                    String records = ((DataSourceContainer) data).getDataSource().getNumberRecords()[2];
                    if(Util.compareStrings(searchValue, id) || Util.compareStrings(searchValue, description)
                            || Util.compareStrings(searchValue, records)){
                        BaseSearchResult ds = createModelLight(id, description, description, id, DataType.DATA_SET);
                        ds.set("records",records);
                        searchData.add(ds);
                    }
                }
            }
            return searchData;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServerSideException(Util.stackTraceToString(e));
        }
    }

    public int getDataPage(String id, int pageSize){
        try{
            List<Object> allDataList = RepoxServiceImpl.getRepoxManager().getDataManager().getAllDataList();
            int showSize = RepoxServiceImpl.getRepoxManager().getDataManager().getShowSize();
            int extra = 0;
            for(int i = 0; i<showSize+extra; i+=pageSize){
                for(int j = i; j<pageSize+i && j<showSize+extra; j++){
                    String modelId = null;
                    if(allDataList.get(j) instanceof DataProvider){
                        DataProvider dataProvider = ((DataProvider) allDataList.get(j));
                        modelId = dataProvider.getId();
                        if(dataProvider.getDataSourceContainers().values().size() == 1)
                            extra++;
                    } else if(allDataList.get(j) instanceof DataSourceContainer){
                        modelId = ((DataSourceContainer) allDataList.get(j)).getDataSource().getId();
                    }

                    if(modelId != null && modelId.equals(id)){
                        return (i/pageSize)+1;
                    }
                }
            }

        } catch (ServerSideException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return -1;
    }

    public List<ModelData> getAllAggregators() throws ServerSideException{return null;}
    public SaveDataResponse saveAggregator(boolean update, AggregatorUI aggregatorUI, int pageSize) throws ServerSideException{return null;}
    public String deleteAggregators(List<AggregatorUI> aggregatorUIs) throws ServerSideException{return null;}
}
