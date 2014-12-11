package pt.utl.ist.repox.dataProvider;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import pt.utl.ist.repox.RepoxConfigurationDefault;
import pt.utl.ist.repox.accessPoint.AccessPoint;
import pt.utl.ist.repox.accessPoint.AccessPointsManagerDefault;
import pt.utl.ist.repox.dataProvider.dataSource.*;
import pt.utl.ist.repox.externalServices.*;
import pt.utl.ist.repox.ftp.DataSourceFtp;
import pt.utl.ist.repox.http.DataSourceHttp;
import pt.utl.ist.repox.marc.*;
import pt.utl.ist.repox.metadataSchemas.MetadataSchemaManager;
import pt.utl.ist.repox.metadataSchemas.MetadataSchemaVersion;
import pt.utl.ist.repox.metadataTransformation.MetadataTransformation;
import pt.utl.ist.repox.metadataTransformation.MetadataTransformationManager;
import pt.utl.ist.repox.oai.DataSourceOai;
import pt.utl.ist.repox.sru.DataSourceSruRecordUpdate;
import pt.utl.ist.repox.task.*;
import pt.utl.ist.repox.util.ConfigSingleton;
import pt.utl.ist.repox.util.TimeUtil;
import pt.utl.ist.repox.util.XmlUtil;
import pt.utl.ist.repox.z3950.*;
import pt.utl.ist.util.CompareDataUtil;
import pt.utl.ist.util.DateUtil;
import pt.utl.ist.util.ExternalServiceUtil;
import pt.utl.ist.util.FileUtil;
import pt.utl.ist.util.exceptions.AlreadyExistsException;
import pt.utl.ist.util.exceptions.IncompatibleInstanceException;
import pt.utl.ist.util.exceptions.InvalidArgumentsException;
import pt.utl.ist.util.exceptions.ObjectNotFoundException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

public class DataManagerDefault implements DataManager{
    //protected static final String ID_REGULAR_EXPRESSION = "[a-zA-Z][a-zA-Z_0-9]*";
    protected static final String ID_REGULAR_EXPRESSION = "[a-zA-Z_0-9]*";
    protected static final int ID_MAX_SIZE = 64;
    private static final Logger log = Logger.getLogger(DataManagerDefault.class);

    protected File dataProvidersFile;
    protected MetadataTransformationManager metadataTransformationManager;
    protected MetadataSchemaManager metadataSchemaManager;
    protected List<DataProvider> dataProviders;
    protected File oldTasksFile;
    protected List<Object> allDataList;
    private int showSize = 0;

    private RepoxConfigurationDefault configuration;

    public synchronized List<DataProvider> getDataProviders() throws DocumentException, IOException {
        return Collections.unmodifiableList(dataProviders);
    }

    public File getDataProvidersFile() {
        return dataProvidersFile;
    }

    public void setDataProvidersFile(File dataProvidersFile) {
        this.dataProvidersFile = dataProvidersFile;
    }

    public MetadataTransformationManager getMetadataTransformationManager() {
        return metadataTransformationManager;
    }

    public void setMetadataTransformationManager(MetadataTransformationManager metadataTransformationManager) {
        this.metadataTransformationManager = metadataTransformationManager;
    }

    public DataManagerDefault(File dataProvidersFile, MetadataTransformationManager metadataTransformationManager,
                              MetadataSchemaManager metadataSchemaManager,
                              File repositoryPath, File oldTasksFile, RepoxConfigurationDefault configuration) throws DocumentException, IOException, ParseException {
        super();
        this.configuration = configuration;
        this.dataProvidersFile = dataProvidersFile;
        this.metadataTransformationManager = metadataTransformationManager;
        this.metadataSchemaManager = metadataSchemaManager;
        this.oldTasksFile = oldTasksFile;

        loadDataProviders(repositoryPath);
        loadAllDataList();
        if(!this.dataProvidersFile.exists()) {
            try {
                saveData();
            }
            catch(IOException e) {
                log.error("Can't create DataProviders configuration file", e);
            }
        }
    }

    private void loadAllDataList(){
        showSize = 0;
        allDataList = new ArrayList<Object>();
        Collections.sort(dataProviders, new CompareDataUtil.DPComparator());
        for(DataProvider dataProvider : dataProviders){
            allDataList.add(dataProvider);
            showSize++;
            if(dataProvider.getDataSourceContainers() != null) {
                List<DataSourceContainer> containers = CompareDataUtil.convertHashToList(dataProvider.getDataSourceContainers());
                Collections.sort(containers, new CompareDataUtil.DSComparator());
                for (DataSourceContainer dataSourceContainer : containers) {
                    allDataList.add(dataSourceContainer);
                    showSize++;
                }
//                if(dataProvider.getDataSourceContainers().size() == 1)
//                    showSize--;
            }
        }
    }

    public List<Object> getAllDataList() {
        return allDataList;
    }

    public int getShowSize() {
        return showSize;
    }

    /******************************************************************************************************************/
    /** READ/WRITE XML file *******************************************************************************************/
    /******************************************************************************************************************/

    /**
     * Save the list of data providers in XML file
     * @throws IOException
     */
    public synchronized void saveData() throws IOException {
        loadAllDataList();
        Document document = DocumentHelper.createDocument();
        Element rootNode = document.addElement("data-providers");

        for(DataProvider dataProvider : dataProviders) {
            rootNode.add(dataProvider.createElement(true));
        }

        // backup file
        File backup = new File(dataProvidersFile.getParentFile().getAbsoluteFile() + File.separator + dataProvidersFile.getName() + ".bak");
        if(backup.exists())
            backup.delete();
        dataProvidersFile.renameTo(backup);

        // todo create a daily and weekly backup file
        XmlUtil.writePrettyPrint(dataProvidersFile, document);
    }

    /**
     * Load the list of data providers from XML file
     * @param repositoryPath
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    protected synchronized void loadDataProviders(File repositoryPath) throws DocumentException, IOException, ParseException {
        dataProviders = new ArrayList<DataProvider>();
        if(!dataProvidersFile.exists()) {
            return;
        }
        dataProviders = loadDataProvidersFromFile(dataProvidersFile, repositoryPath);
    }


    /**
     * Load the list of data providers from XML file
     * @param file2Read
     * @return
     * @throws DocumentException
     * @throws IOException
     */
    public List<DataProvider> loadDataProvidersFromFile(File file2Read, File repositoryPath) throws DocumentException, IOException, ParseException {
        List<DataProvider> dataProvidersLoaded = new ArrayList<DataProvider>();

        SAXReader reader = new SAXReader();
        Document document = reader.read(file2Read);

        if(configuration != null && configuration.getCurrentServerOAIUrl() != null){
            ExternalServiceUtil.replaceAllExternalServices(document, configuration.getCurrentServerOAIUrl());
            XmlUtil.writePrettyPrint(dataProvidersFile,document);
        }

        if(!document.getRootElement().elements("provider").isEmpty()) {
            for (Element currentDataProviderElement : (List<Element>) document.getRootElement().elements("provider")) {

                String providerId = currentDataProviderElement.attributeValue("id");
                String providerName = currentDataProviderElement.elementText("name");
                String providerCountry = currentDataProviderElement.elementText("country");
                String providerDescription = currentDataProviderElement.elementText("description");

                HashMap<String, DataSourceContainer> dataSourceContainers = new HashMap<String, DataSourceContainer>();

                DataProvider provider = new DataProvider(providerId, providerName, providerCountry, providerDescription, dataSourceContainers);
                for (Element currentDataSourceElement : (List<Element>) currentDataProviderElement.elements("source")) {
                    String id = currentDataSourceElement.attributeValue("id");
                    String description = currentDataSourceElement.elementText("description");
                    String metadataFormat = currentDataSourceElement.attributeValue("metadataFormat");
                    String schema = currentDataSourceElement.attributeValue("schema");
                    String namespace = currentDataSourceElement.attributeValue("namespace");
                    String lastIngest = currentDataSourceElement.attributeValue("lastIngest");
                    String sample = currentDataSourceElement.attributeValue("sample");
                    String status = currentDataSourceElement.attributeValue("status");

                    String isSampleStr = currentDataSourceElement.attributeValue("isSample");
                    if(isSampleStr == null)
                        isSampleStr = "false";
                    boolean isSample = Boolean.valueOf(isSampleStr);

                    if(namespace == null){
                        namespace = MetadataTransformationManager.getNamespace(metadataFormat);
                    }
                    if(schema == null){
                        schema = MetadataTransformationManager.getSchema(metadataFormat);
                    }

                    // Create RecordIdPolicy
                    Element recordIdPolicyNode = currentDataSourceElement.element("recordIdPolicy");
                    String recordIdPolicyClass = recordIdPolicyNode.attributeValue("type");
                    RecordIdPolicy recordIdPolicy = null;
                    if(recordIdPolicyClass.equals(IdGenerated.class.getSimpleName())) {
                        recordIdPolicy = new IdGenerated();
                    }
                    else if(recordIdPolicyClass.equals(IdProvided.class.getSimpleName())) {
                        recordIdPolicy = new IdProvided();
                    }
                    else if(recordIdPolicyClass.equals(IdExtracted.class.getSimpleName())) {
                        String identifierXpath = recordIdPolicyNode.element("idXpath").getText();
                        Map<String, String> namespaces = new TreeMap<String, String>();
                        Element namespacesElement = recordIdPolicyNode.element("namespaces");
                        if(namespacesElement != null) {
                            List<Element> namespaceElement = namespacesElement.elements("namespace");
                            for (Element currentNamespace : namespaceElement) {
                                namespaces.put(currentNamespace.elementText("namespacePrefix"),
                                        currentNamespace.elementText("namespaceUri"));
                            }
                        }

                        recordIdPolicy = new IdExtracted(identifierXpath, namespaces);
                    }
                    else {
                        throw new RuntimeException("Invalid RecordIdPolicy of class " + recordIdPolicyClass);
                    }


                    //Create MetadataTransformations
                    Map<String, MetadataTransformation> metadataTransformations = new HashMap<String, MetadataTransformation>();
                    for(Element metadataTransformationElement: (List<Element>) currentDataSourceElement.element("metadataTransformations").elements("metadataTransformation")) {
                        String transformationId = metadataTransformationElement.getText();
                        MetadataTransformation metadataTransformation = metadataTransformationManager.loadMetadataTransformation(transformationId);
                        if(metadataTransformation != null)
                            metadataTransformations.put(metadataTransformation.getId(), metadataTransformation);
                    }

                    //Create DataSource
                    DataSource dataSource = null;

                    DataSource existDataSource = getDataSource(id);
                    if(existDataSource != null){
                        // create a new ID for the imported data set
                        int i = 0;
                        while (true){
                            if(getDataSourceContainer(id + i) == null){
                                id = (id + i);
                                break;
                            }
                            i++;
                        }
                    }


                    String dataSourceType = currentDataSourceElement.attribute("type").getText();
                    if(dataSourceType.equals("DataSourceOai")) {
                        String oaiSource = currentDataSourceElement.elementText("oai-source");
                        String oaiSet = (currentDataSourceElement.element("oai-set") != null ? currentDataSourceElement.elementText("oai-set") : null);
                        dataSource = new DataSourceOai(provider, id, description, schema, namespace, metadataFormat,
                                oaiSource, oaiSet, new IdProvided(), metadataTransformations);
                    } else if(dataSourceType.equals("DataSourceSruRecordUpdate")) {
                        dataSource = new DataSourceSruRecordUpdate(provider, id, description, schema, namespace, metadataFormat,
                                recordIdPolicy, metadataTransformations);                        
                    }
                    else if(dataSourceType.equals("DataSourceDirectoryImporter")) {
                        String sourcesDirPath = currentDataSourceElement.elementText("sourcesDirPath");
                        String extractStrategyString = currentDataSourceElement.elementText("fileExtract");
                        Element currentRetrieveStrategy = currentDataSourceElement.element("retrieveStrategy");

                        String retrieveStrategyString = null;
                        if(currentRetrieveStrategy != null){
                            retrieveStrategyString = currentRetrieveStrategy.attributeValue("type");
                        }

                        FileRetrieveStrategy retrieveStrategy;
                        //FTP
                        if(retrieveStrategyString != null && retrieveStrategyString.equals(DataSourceFtp.class.getName())){

                            String server = currentRetrieveStrategy.elementText("server");

                            String user = currentRetrieveStrategy.elementText("user");
                            String password = currentRetrieveStrategy.elementText("password");

                            String idType;
                            if(user != null && password != null){
                                idType = DataSourceFtp.NORMAL;
                            }
                            else{
                                idType = DataSourceFtp.ANONYMOUS;
                            }
                            String ftpPath = currentRetrieveStrategy.elementText("folderPath");
                            retrieveStrategy = new DataSourceFtp(server, user, password, idType, ftpPath);
                        }
                        //HTTP
                        else if(retrieveStrategyString != null && retrieveStrategyString.equals(DataSourceHttp.class.getName())){

                            String url = currentRetrieveStrategy.elementText("url");
                            retrieveStrategy = new DataSourceHttp(url);
                        }
                        else{
                            //FOLDER
                            retrieveStrategy = new DataSourceFolder();
                        }

                        CharacterEncoding characterEncoding = null;
                        String recordXPath = null;
                        Map<String, String> namespaces = null;
                        FileExtractStrategy extractStrategy = null;

                        if(extractStrategyString.equals(Iso2709FileExtract.class.getSimpleName())) {
                            characterEncoding = CharacterEncoding.get(currentDataSourceElement.attributeValue("characterEncoding"));
                            String isoImplementationClass = currentDataSourceElement.attributeValue("isoImplementationClass");
                            extractStrategy = new Iso2709FileExtract(isoImplementationClass);
                        } else if(extractStrategyString.equals(MarcXchangeFileExtract.class.getSimpleName())) {
                            extractStrategy = new MarcXchangeFileExtract();
                        } else if(extractStrategyString.equals(SimpleFileExtract.class.getSimpleName())) {
                            extractStrategy = new SimpleFileExtract();

                            Element splitRecordsElement = currentDataSourceElement.element("splitRecords");
                            if(splitRecordsElement != null){
                                recordXPath = splitRecordsElement.elementText("recordXPath");

                                namespaces = new TreeMap<String, String>();
                                Element namespacesElement = splitRecordsElement.element("namespaces");
                                if(namespacesElement != null) {
                                    List<Element> namespaceElement = namespacesElement.elements("namespace");
                                    for (Element currentNamespace : namespaceElement) {
                                        namespaces.put(currentNamespace.elementText("namespacePrefix"),
                                                currentNamespace.elementText("namespaceUri"));
                                    }
                                }
                            }
                        }
                        dataSource = new DataSourceDirectoryImporter(provider, id, description, schema, namespace, metadataFormat, extractStrategy,
                                retrieveStrategy, characterEncoding, sourcesDirPath, recordIdPolicy, metadataTransformations, recordXPath, namespaces);
                    }
                    else if(dataSourceType.equals("DataSourceZ3950")) {
                        Element targetElement = currentDataSourceElement.element("target");
                        String targetAddress = targetElement.elementText("address");
                        int targetPort = Integer.parseInt(targetElement.elementText("port"));
                        String targetDatabase = targetElement.elementText("database");
                        String targetUser = targetElement.elementText("user");
                        String targetPassword = targetElement.elementText("password");
                        String targetRecordSyntax= targetElement.elementText("recordSyntax");
                        CharacterEncoding targetCharacterEncoding = CharacterEncoding.get(targetElement.elementText("charset"));

                        Target target = new Target(targetAddress, targetPort, targetDatabase, targetUser, targetPassword,
                                targetCharacterEncoding, targetRecordSyntax);

                        HarvestMethod harvestMethod = null;

                        String harvestMethodString = currentDataSourceElement.elementText("harvestMethod");

                        if(harvestMethodString.equals(TimestampHarvester.class.getSimpleName())) {
                            String timestampString = currentDataSourceElement.elementText("earliestTimestamp");
                            try {
                                Date earliestTimestamp = DateUtil.string2Date(timestampString, "yyyyMMdd");

                                harvestMethod = new TimestampHarvester(target, earliestTimestamp);
                            }
                            catch (ParseException e) {
                                log.error("Error parsing date: " + timestampString, e);
                                break;
                            }
                        } else if(harvestMethodString.equals(IdListHarvester.class.getSimpleName())) {
                            String filePath = currentDataSourceElement.elementText("idListFile");
                            File idListFile = new File(filePath);

                            harvestMethod = new IdListHarvester(target, idListFile);

                        } else if(harvestMethodString.equals(IdSequenceHarvester.class.getSimpleName())) {
                            String maximumIdString =  currentDataSourceElement.elementText("maximumId");
                            Long maximumId = (maximumIdString != null && !maximumIdString.isEmpty() ? Long.valueOf(maximumIdString) : null);

                            harvestMethod = new IdSequenceHarvester(target, maximumId);
                        }

                        dataSource = new DataSourceZ3950(provider, id, description, schema, namespace, harvestMethod, recordIdPolicy, metadataTransformations);
                    }
                    else {
                        throw new RuntimeException("Loading configuration from Data Source of type " + dataSourceType + " not implemented");

                    }

                    parseOldTasks(dataSource);

                    boolean removeSynFile = false;
                    if(dataSource != null){
                        // Add generic Data Source Data
                        if(status != null && !file2Read.getParentFile().getName().equals("temp")){
                            if(status.equals(DataSource.StatusDS.OK.toString())){
                                dataSource.setStatus(DataSource.StatusDS.OK);
                            }
                            else if(status.equals(DataSource.StatusDS.ERROR.toString())){
                                dataSource.setStatus(DataSource.StatusDS.ERROR);
                            }
                            else if(status.equals(DataSource.StatusDS.WARNING.toString())){
                                dataSource.setStatus(DataSource.StatusDS.WARNING);
                            }
                            else if(status.equals(DataSource.StatusDS.RUNNING.toString())){
                                dataSource.setStatus(DataSource.StatusDS.RUNNING);
                            }
                            else if(status.equals(DataSource.StatusDS.CANCELED.toString())){
                                dataSource.setStatus(DataSource.StatusDS.CANCELED);
                            }else if(status.equals(DataSource.StatusDS.PRE_PROCESS_ERROR.toString())){
                                dataSource.setStatus(DataSource.StatusDS.PRE_PROCESS_ERROR);
                            }else if(status.equals(DataSource.StatusDS.POST_PROCESS_ERROR.toString())){
                                dataSource.setStatus(DataSource.StatusDS.POST_PROCESS_ERROR);
                            }
                        }

                        if(lastIngest == null && !file2Read.getParentFile().getName().equals("temp")){
                            // dataProviders.xml old version
                            lastIngest = dataSource.getSynchronizationDate(new File(new File(repositoryPath, id), "synchronization-date.txt"));
                            removeSynFile = true;
                        }
                        else if(file2Read.getParentFile().getName().equals("temp")){
                            lastIngest = "";
                        }
                        if(lastIngest != null && !lastIngest.equals("")){
                            try{
                                dataSource.setLastUpdate(DateUtil.string2Date(lastIngest, TimeUtil.LONG_DATE_FORMAT));
                            }
                            catch (ParseException e){
                                dataSource.setLastUpdate(DateUtil.string2Date(lastIngest, TimeUtil.SHORT_DATE_FORMAT));
                            }
                        }

                        if(sample == null){
                            // dataProviders.xml old version
                            sample = String.valueOf(dataSource.getSampleNumber(new File(new File(repositoryPath, id), "synchronization-date.txt")));
                            removeSynFile = true;
                        }
                        if(!sample.equals("") && !file2Read.getParentFile().getName().equals("temp")){
                            dataSource.setMaxRecord4Sample(Integer.valueOf(sample));
                        }

                        if(removeSynFile){
                            // remove file synchronization-date.txt file
                            File dataSourceSyncDate = new File(new File(repositoryPath, id), "synchronization-date.txt");
                            if(dataSourceSyncDate.exists()) {
                                boolean result = dataSourceSyncDate.delete();
                                if(result)
                                    log.info("Deleted synchronization-date.txt file with success from Data Source with id " + id);
                                else
                                    log.error("Unable to delete synchronization-date.txt file from Data Source with id " + id);
                            }
                        }

                        String marcFormat = currentDataSourceElement.attributeValue("marcFormat");
                        if(marcFormat != null && !marcFormat.isEmpty())
                            dataSource.setMarcFormat(marcFormat);

                        // Load external services data
                        List servicesList = currentDataSourceElement.selectNodes("restServices/restService");
                        String executeType = currentDataSourceElement.valueOf("restServices/@executeType");
                        if(executeType != null && !executeType.isEmpty())
                            dataSource.setExternalServicesRunType(ExternalServiceStates.ContainerType.valueOf(executeType));

                        for(Object node: servicesList) {
                            Node n = (Node) node;
                            String serviceId = n.valueOf("@id");
                            String serviceType = n.valueOf("@type");
                            String enabled = n.valueOf("@isEnabled");
                            boolean isEnabled = Boolean.valueOf((enabled == null || enabled.isEmpty()) ? "true" : enabled);
                            String serviceUri = n.valueOf("@uri");
                            String serviceStatusUri = n.valueOf("@statusUri");
                            String externalResultsUri = n.valueOf("@externalResultsUri");
                            String serviceName = n.valueOf("@name");
                            String externalServiceType = n.valueOf("@externalServiceType");
                            if(externalServiceType == null || externalServiceType.isEmpty())
                                externalServiceType = ExternalServiceType.MONITORED.name();

                            ExternalRestService externalRestService;
                            if(externalServiceType.equals(ExternalServiceType.NO_MONITOR.name())){
                                externalRestService = new ExternalServiceNoMonitor(serviceId,
                                        serviceName,serviceUri,dataSource);
                            } else{
                                externalRestService = new ExternalRestService(serviceId,
                                        serviceName,serviceUri,serviceStatusUri,serviceType, ExternalServiceType.valueOf(externalServiceType));
                            }
                            externalRestService.setEnabled(isEnabled);
                            if(externalResultsUri != null && !externalResultsUri.isEmpty())
                                externalRestService.setExternalResultsUri(externalResultsUri);

                            List parametersList = n.selectNodes("parameters/parameter");
                            for(Object nodeP: parametersList) {
                                Node parameterNode = (Node) nodeP;
                                String parameterName = parameterNode.valueOf("@name");
                                String parameterValue = parameterNode.valueOf("@value");
                                String parameterType = parameterNode.valueOf("@type");
                                boolean parameterRequired = Boolean.parseBoolean(parameterNode.valueOf("@required"));
                                String exampleStr = parameterNode.valueOf("@example");
                                String semanticsStr = parameterNode.valueOf("@semantics");

                                ServiceParameter serviceParameter = new ServiceParameter(parameterName,
                                        parameterType,parameterRequired,exampleStr,semanticsStr);
                                serviceParameter.setValue(parameterValue);
                                externalRestService.getServiceParameters().add(serviceParameter);
                            }
                            dataSource.getExternalRestServices().add(externalRestService);
                        }

                        loadDataSourceTags(currentDataSourceElement,dataSource);

                        // export path
                        if(currentDataSourceElement.elementText("exportDirPath") != null &&
                                !currentDataSourceElement.elementText("exportDirPath").isEmpty())
                            dataSource.setExportDir(currentDataSourceElement.elementText("exportDirPath"));
                        else{
                            File newExportDir = new File(repositoryPath.getAbsolutePath() + File.separator
                                    + dataSource.getId() + File.separator + "export");
//                            FileUtils.forceMkdir(newExportDir);
                            dataSource.setExportDir(newExportDir.getAbsolutePath());
                        }

                        // Create new MDR schema if it doesn't exist
                        boolean exists = metadataSchemaManager.schemaExists(metadataFormat);

                        if(!exists){
                            List<MetadataSchemaVersion> metadataSchemaVersions = new ArrayList<MetadataSchemaVersion>();
                            metadataSchemaVersions.add(new MetadataSchemaVersion(1.0,schema));
                            metadataSchemaManager.saveMetadataSchema(null,metadataFormat,null,namespace,null,null,metadataSchemaVersions,true);
                        }

                        dataSource.setIsSample(isSample);

                        dataSourceContainers.put(dataSource.getId(), new DataSourceContainerDefault(dataSource));
                    }
                }

                if(getDataProvider(provider.getId()) != null){
                    // create a new ID for the imported data provider
                    provider.setId(DataProvider.generateId(provider.getName()));
                }
                dataProvidersLoaded.add(provider);
            }
        }

        //save new dataProviders.xml format
        if(dataProviders != null && dataProviders.size() > 0){
            for (DataProvider dataProviderLoaded : dataProvidersLoaded) {
                try {
                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataProviderLoaded.getDataSourceContainers());
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                dataProviders.add(dataProviderLoaded);
            }
        }
        else{
            dataProviders = dataProvidersLoaded;
        }
        saveData();

        return dataProvidersLoaded;
    }

    protected void loadDataSourceTags(Element currentDataSourceElement, DataSource dataSource){
        List<Node> servicesList = currentDataSourceElement.selectNodes("tags/tag");
        for(Node tagNode : servicesList) {
            String name = tagNode.valueOf("@name");
            dataSource.getTags().add(new DataSourceTag(name));
        }
    }

    public MessageType importDataProviders(File file2read, File repoPath) {
        // do not change due
        try {
            List<DataProvider> dataProvidersToImport = loadDataProvidersFromFile(file2read, repoPath);

            for (int i = 0; i < dataProvidersToImport.size(); i++) {
                DataProvider actualDataProvider = dataProvidersToImport.get(i);

                DataProvider loadedDataProvider = getDataProvider(actualDataProvider.getId());

                List<DataSourceContainer> currentDataSources = (loadedDataProvider != null && loadedDataProvider.getDataSourceContainers() != null ?
                        new ArrayList<DataSourceContainer>(loadedDataProvider.getDataSourceContainers().values()) : new ArrayList<DataSourceContainer>());
                List<DataSourceContainer> newDataSources = actualDataProvider.getDataSourceContainers() != null ?
                        new ArrayList<DataSourceContainer>(actualDataProvider.getDataSourceContainers().values()) : new ArrayList<DataSourceContainer>();
                List<DataSourceContainer> dataSourcesToInitialize = new ArrayList<DataSourceContainer>();


                for (int j = 0; j < newDataSources.size(); j++) {
                    DataSourceContainer dataSourceContainer = newDataSources.get(j);
                    // Try to remove Data Source, if it's not there it must be initialized

                    deleteDataSourceContainer(dataSourceContainer.getDataSource().getId());
                    dataSourcesToInitialize.add(dataSourceContainer);

                    currentDataSources.add(j, dataSourceContainer);
                }

                HashMap<String, DataSourceContainer> dataSourceContainers = new HashMap<String, DataSourceContainer>();
                for (DataSourceContainer currentDataSource : currentDataSources) {
                    dataSourceContainers.put(currentDataSource.getDataSource().getId(), currentDataSource);

                    currentDataSource.getDataSource().initAccessPoints();
                }
                ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataSourceContainers);

                actualDataProvider.setDataSourceContainers(dataSourceContainers);

                if (loadedDataProvider != null) {
                    updateDataProvider(actualDataProvider, actualDataProvider.getId());
                } else {
                    addDataProvider(actualDataProvider);
                }
                return MessageType.OK;
            }
        } catch (DocumentException e) {
            return MessageType.OTHER;
        } catch (IOException e) {
            return MessageType.OTHER;
        } catch (ParseException e) {
            return MessageType.OTHER;
        } catch (SQLException e) {
            return MessageType.OTHER;
        } catch (AlreadyExistsException e) {
            return MessageType.ALREADY_EXISTS;
        } catch (ObjectNotFoundException e) {
            return MessageType.NOT_FOUND;
        }
        return MessageType.OTHER;
    }

    public void parseOldTasks(DataSource dataSource) {
        if(oldTasksFile.exists()){
            try{
                SAXReader reader = new SAXReader();
                Document recordsDocument = reader.read(oldTasksFile);
                List list = recordsDocument.selectNodes("//old-tasks/oldTask");

                for(Object node: list) {
                    Node n = (Node) node;
                    String dataSet = n.valueOf("dataSourceSet");
                    if(dataSet.equals(dataSource.getId())) {
                        String id = n.valueOf("@id");
                        String logName = n.valueOf("logName");
                        String ingestType = n.valueOf("ingestType");
                        String status = n.valueOf("status");
                        String retries = n.valueOf("retries");
                        String maxRetries = n.valueOf("retries/@max");
                        String retriesDelay = n.valueOf("retries/@delay");
                        String time = n.valueOf("time");
                        String records = n.valueOf("records");
                        OldTask oldTask = new OldTask(dataSource,id,logName,ingestType,status,retries,
                                maxRetries,retriesDelay,time,records);

                        dataSource.getOldTasksList().add(oldTask);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else{
            log.warn("The old tasks file does not exist.");
        }
    }

    public synchronized void saveOldTask(OldTask oldTask) {
        try {
            SAXReader reader = new SAXReader();

            Document document;
            if(oldTasksFile.exists())
                document = reader.read(oldTasksFile);
            else{
                document = DocumentHelper.createDocument();
                document.addElement("old-tasks");
            }

            Element oldTaskEl = document.getRootElement().addElement( "oldTask" );
            oldTaskEl.addAttribute("id", oldTask.getId());
            oldTaskEl.addElement( "time" ).addText(oldTask.getYear()+"-"+ oldTask.getMonth()+"-"+ oldTask.getDay()+
                    " " + oldTask.getHours() + ":" + oldTask.getMinutes());
            oldTaskEl.addElement( "dataSourceSet" ).addText(oldTask.getDataSource().getId());
            oldTaskEl.addElement( "logName" ).addText(oldTask.getLogName());
            oldTaskEl.addElement( "ingestType" ).addText(oldTask.getIngestType());
            oldTaskEl.addElement( "status" ).addText(oldTask.getStatus());
            oldTaskEl.addElement( "records" ).addText(oldTask.getRecords());
            Element retries = oldTaskEl.addElement("retries");
            retries.addAttribute("max", oldTask.getRetryMax());
            retries.addAttribute("delay", oldTask.getDelay());
            retries.addText(oldTask.getRetries());

            try{
                XmlUtil.writePrettyPrint(oldTasksFile, document);
            }
            catch(IOException e){
                log.debug(e.getMessage());
                //System.out.println(e.getMessage());
            }
        }
        catch(Exception e) { e.printStackTrace(); }
    }

    public void removeOldTasks(String dataSourceId) {
        if(!oldTasksFile.exists()) {
            Document document = DocumentHelper.createDocument();
            document.addElement("old-tasks");
            try {
                XmlUtil.writePrettyPrint(oldTasksFile, document);
            } catch (IOException e) {
                //System.out.println(e.getMessage());
                log.error("Error writing old tasks file");
            }
        }
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(oldTasksFile);
            List list = document.selectNodes("//old-tasks/oldTask");

            for(Object node: list) {
                Node n = (Node) node;
                String dataSet = n.valueOf("dataSourceSet");
                if(dataSet.equals(dataSourceId))
                    n.detach();
            }

            XMLWriter output = new XMLWriter(new FileWriter( new File(oldTasksFile.getPath())));
            output.write( document );
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void removeLogsAndOldTasks(String dataSetId) throws IOException, DocumentException {
        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getDataManager().getDataSourceContainer(dataSetId).
                getDataSource().getOldTasksList().clear();
        removeOldTasks(dataSetId);
        File logsDir = new File(ConfigSingleton.getRepoxContextUtil().getRepoxManager().getConfiguration().getRepositoryPath()
                + File.separator + dataSetId,"logs");
        FileUtils.deleteDirectory(logsDir);
    }

    /******************************************************************************************************************/
    /** DATA PROVIDER's ***********************************************************************************************/
    /******************************************************************************************************************/

    public DataProvider createDataProvider(String name, String country, String description) throws IOException, AlreadyExistsException {
        DataProvider newDataProvider = new DataProvider();
        newDataProvider.setName(name);
        newDataProvider.setCountry(country);
        newDataProvider.setDescription(description);
        newDataProvider.setDataSourceContainers(new HashMap<String, DataSourceContainer>());
        newDataProvider.setId(DataProvider.generateId(newDataProvider.getName()));

        return addDataProvider(newDataProvider);
    }

    /**
     * Updates the data provider
     * @throws IOException
     * @return MessageType
     */
    public DataProvider updateDataProvider(String id, String name, String country, String description) throws ObjectNotFoundException, IOException {
        DataProvider dataProvider = getDataProvider(id);
        if(dataProvider != null){
            dataProvider.setName(name);
            dataProvider.setCountry(country);
            dataProvider.setDescription(description);
            return updateDataProvider(dataProvider, id);
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }

    /**
     * Deletes the DataProvider with id dataProviderId
     * @param dataProviderId
     * @throws IOException
     * @throws DocumentException
     */
    public void deleteDataProvider(String dataProviderId) throws IOException, ObjectNotFoundException {
        DataProvider dataProvider = getDataProvider(dataProviderId);
        if(dataProvider != null){

            Iterator<DataProvider> iteratorDataProvider = dataProviders.iterator();
            while (iteratorDataProvider.hasNext()) {
                DataProvider currentDataProvider = iteratorDataProvider.next();
                if(currentDataProvider.getId().equals(dataProviderId)) {
                    if(currentDataProvider.getDataSourceContainers() != null &&
                            currentDataProvider.getDataSourceContainers().size() > 0) {

                        Object[] list = currentDataProvider.getDataSourceContainers().keySet().toArray();
                        for (int i = list.length - 1; i > -1; i--) {
                            deleteDataSource((String)list[i]);
                        }
/*
                        Iterator<DataSourceContainer> iteratorDataSourceContainer = currentDataProvider.getDataSourceContainers().values().iterator();
                        while (iteratorDataSourceContainer.hasNext()){
                            deleteDataSource(iteratorDataSourceContainer.next().getDataSource().getId());
                            iteratorDataSourceContainer.remove();
                        }*/
                    }
                    iteratorDataProvider.remove();
                    saveData();
                    return;
                }
            }
        }
        else{
            throw new ObjectNotFoundException(dataProviderId);
        }
    }




    /***
     * Save a data provider (used by import dataProviders from file)
     * @param newDataProvider
     * @throws IOException
     * @throws DocumentException
     * @return MessageType
     */
    //todo to be private
    public synchronized DataProvider addDataProvider(DataProvider newDataProvider) throws IOException, AlreadyExistsException {
        if(checkIfDataProviderExists(newDataProvider.getName())){
            throw new AlreadyExistsException(newDataProvider.getName());
        }
        dataProviders.add(newDataProvider);
        saveData();
        return newDataProvider;
    }

    /**
     * Used by REST service
     * @param name
     * @return DataProvider
     */
    public DataProvider getDataProviderByName(String name){
        for (DataProvider dataProvider : dataProviders) {
            if(dataProvider.getName().equals(name))
                return dataProvider;
        }
        return null;
    }

    /**
     * Gets the DataProvider with id dataProviderId from the configuration file if it exists or null otherwise.
     *
     * @param dataProviderId
     * @return DataProvider with id dataProviderId if exists, null otherwise
     * @throws DocumentException
     * @throws IOException
     */
    public DataProvider getDataProvider(String dataProviderId) {
        for (DataProvider currentDataProvider : dataProviders) {
            if (currentDataProvider.getId().equals(dataProviderId)) {
                return currentDataProvider;
            }
        }
        return null;
    }

    public synchronized DataProvider updateDataProvider(DataProvider dataProvider, String oldDataProviderId) throws IOException, ObjectNotFoundException {
        Iterator<DataProvider> iteratorDataProvider = dataProviders.iterator();

        while (iteratorDataProvider.hasNext()) {
            DataProvider currentDataProvider = iteratorDataProvider.next();
            if(currentDataProvider.getId().equals(oldDataProviderId)) {
                iteratorDataProvider.remove();
                dataProviders.add(dataProvider);
                saveData();
                return dataProvider;
            }
        }
        throw new ObjectNotFoundException(oldDataProviderId);
    }

    /**
     * Check if data provider exists
     * @param dataProviderName
     * @return boolean
     */
    private boolean checkIfDataProviderExists(String dataProviderName){
        for (DataProvider currentDataProvider : dataProviders) {
            if(currentDataProvider.getName().equals(dataProviderName)){
                return true;
            }
        }
        return false;
    }

    /******************************************************************************************************************/
    /** DATA CONTAINER ************************************************************************************************/
    /******************************************************************************************************************/

    @Override
    public MessageType addDataSourceContainer(DataSourceContainer dataSourceContainer, String dataProviderId){
        try {
            DataProvider dataProvider = getDataProvider(dataProviderId);
            dataProvider.getDataSourceContainers().put(dataSourceContainer.getDataSource().getId(), dataSourceContainer);
            // todo add dataSource to database
            saveData();
            return MessageType.OK;
        } catch (IOException e) {
            return MessageType.OTHER;
        }
    }

    @Override
    public MessageType updateDataSourceContainer(DataSourceContainer newDataSourceContainer, String oldDataSourceId){
        try {
            for (DataProvider currentDataProvider : dataProviders) {
                DataSourceContainer currentDataSourceContainer = currentDataProvider.getDataSourceContainers().get(oldDataSourceId);

                if(currentDataSourceContainer != null){
                    if(!newDataSourceContainer.getDataSource().getId().equals(oldDataSourceId)) {
                        // database - update id
                        updateDataSourceId(oldDataSourceId, newDataSourceContainer.getDataSource().getId());
                    }
                    currentDataProvider.getDataSourceContainers().remove(oldDataSourceId).getDataSource();
                    currentDataProvider.getDataSourceContainers().put(newDataSourceContainer.getDataSource().getId(), newDataSourceContainer);

                    // update the data source container HashMap
                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().
                            initialize(currentDataProvider.getDataSourceContainers());

                    saveData();
                    break;
                }
            }
            return MessageType.OK;
        } catch (SQLException e) {
            return MessageType.OTHER;
        } catch (DocumentException e) {
            return MessageType.OTHER;
        } catch (IOException e) {
            return MessageType.OTHER;
        }
    }

    @Override
    public void deleteDataSourceContainer(String dataSourceId) throws IOException {
        deleteDataSource(dataSourceId);
    }

    @Override
    public DataSourceContainer getDataSourceContainer(String dataSourceId) throws DocumentException, IOException {
        for (DataProvider currentDataProvider : dataProviders) {
            if(currentDataProvider.getDataSourceContainers() != null && currentDataProvider.getDataSourceContainers().size() > 0) {
                DataSourceContainer dataSourceContainer = currentDataProvider.getDataSourceContainers().get(dataSourceId);
                if (dataSourceContainer != null) {
                    return dataSourceContainer;
                }
            }
        }
        return null;
    }

    /******************************************************************************************************************/
    /** DATA SOURCE ***************************************************************************************************/
    /******************************************************************************************************************/
    public DataSource createDataSourceOai(String dataProviderId, String id, String description,
                                          String schema, String namespace, String metadataFormat, String oaiSourceURL,
                                          String oaiSet, Map<String, MetadataTransformation> metadataTransformations,
                                          List<ExternalRestService> externalRestServices, String marcFormat) throws DocumentException,
            SQLException, IOException, ObjectNotFoundException, AlreadyExistsException, InvalidArgumentsException {
        if(getDataSourceContainer(id) == null){
            if(isIdValid(id)){
                DataProvider dataProvider = getDataProvider(dataProviderId);
                if(dataProvider != null){
                    //validate the URL server
                    if (!oaiSourceURL.startsWith("http://") &&
                            !oaiSourceURL.startsWith("https://")) {
                        oaiSourceURL = "http://" + oaiSourceURL;
                    }
                    if(new java.net.URL(oaiSourceURL).openConnection().getHeaderField(0) != null && FileUtil.checkUrl(oaiSourceURL)){
                        DataSource newDataSource = new DataSourceOai(dataProvider, id, description, schema, namespace, metadataFormat,
                                oaiSourceURL, oaiSet, new IdProvided(), new TreeMap<String, MetadataTransformation>());

                        dataProvider.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                        newDataSource.initAccessPoints();
                        newDataSource.setMetadataTransformations(metadataTransformations);
                        newDataSource.setExternalRestServices(externalRestServices);
                        newDataSource.setMarcFormat(marcFormat);

                        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataProvider.getDataSourceContainers());
                        updateDataProvider(dataProvider, dataProviderId);
                        return newDataSource;
                    }
                    else{
                        throw new InvalidArgumentsException("oaiURL");
                    }
                }
                else{
                    throw new ObjectNotFoundException(dataProviderId);
                }
            }
            else{
                throw new InvalidArgumentsException("id");
            }
        }
        else{
            throw new AlreadyExistsException(id);
        }
    }

    public DataSource createDataSourceSruRecordUpdate(String dataProviderId, String id, String description,
            String schema, String namespace, String metadataFormat, 
            Map<String, MetadataTransformation> metadataTransformations,
            List<ExternalRestService> externalRestServices, String marcFormat)
            throws DocumentException, SQLException, IOException, ObjectNotFoundException,
            AlreadyExistsException, InvalidArgumentsException {
        if (getDataSourceContainer(id) == null) {
            if (isIdValid(id)) {
                DataProvider dataProvider = getDataProvider(dataProviderId);
                if (dataProvider != null) {
                        DataSource newDataSource = new DataSourceSruRecordUpdate(dataProvider, id, description,
                                schema, namespace, metadataFormat, 
                                new IdGenerated(), new TreeMap<String, MetadataTransformation>());

                        dataProvider.getDataSourceContainers().put(newDataSource.getId(),
                                new DataSourceContainerDefault(newDataSource));
                        newDataSource.initAccessPoints();
                        newDataSource.setMetadataTransformations(metadataTransformations);
                        newDataSource.setExternalRestServices(externalRestServices);
                        newDataSource.setMarcFormat(marcFormat);

                        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(
                                dataProvider.getDataSourceContainers());
                        updateDataProvider(dataProvider, dataProviderId);
                        return newDataSource;
                } else {
                    throw new ObjectNotFoundException(dataProviderId);
                }
            } else {
                throw new InvalidArgumentsException("id");
            }
        } else {
            throw new AlreadyExistsException(id);
        }
    }
    
    
    /**
     * createDataSourceZ3950IdList
     * @param dataProviderId
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param address
     * @param port
     * @param database
     * @param user
     * @param password
     * @param recordSyntax
     * @param charset
     * @param filePath
     * @param recordIdPolicyClass
     * @param idXpath
     * @param metadataTransformations
     * @return MessageType
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public DataSource createDataSourceZ3950IdList(String dataProviderId, String id, String description, String schema, String namespace,
                                                  String address, String port, String database, String user, String password,
                                                  String recordSyntax, String charset, String filePath,
                                                  String recordIdPolicyClass, String idXpath, Map<String, String> namespaces,
                                                  Map<String, MetadataTransformation> metadataTransformations,
                                                  List<ExternalRestService> externalRestServices) throws DocumentException, IOException, ParseException, ObjectNotFoundException, AlreadyExistsException, InvalidArgumentsException, SQLException {
        if(getDataSourceContainer(id) == null){
            if(isIdValid(id)){
                DataProvider dataProvider = getDataProvider(dataProviderId);

                if(dataProvider != null){
                    if(charset.equals("")){
                        throw new InvalidArgumentsException("charset");
                    }
                    CharacterEncoding targetCharacterEncoding = CharacterEncoding.get(charset);
                    Target target = new Target(address, Integer.valueOf(port), database, user, password, targetCharacterEncoding, recordSyntax);

                    File newFile = IdListHarvester.getIdListFilePermanent();

                    HarvestMethod harvestMethod;
                    if(!newFile.getParentFile().getAbsolutePath().equalsIgnoreCase(new File(filePath).getParentFile().getAbsolutePath())){
                        FileUtils.copyFile(new File(filePath), newFile);
                        harvestMethod = new IdListHarvester(target, newFile);
                    }
                    else{
                        harvestMethod = new IdListHarvester(target, new File(filePath));
                    }

                    RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                    DataSource newDataSource = new DataSourceZ3950(dataProvider, id, description, schema, namespace,
                            harvestMethod, recordIdPolicy, new TreeMap<String, MetadataTransformation>());

                    DataSourceContainerDefault DataSourceContainerDefault = new DataSourceContainerDefault(newDataSource);
                    dataProvider.getDataSourceContainers().put(newDataSource.getId(), DataSourceContainerDefault);
                    newDataSource.initAccessPoints();
                    newDataSource.setMetadataTransformations(metadataTransformations);
                    newDataSource.setExternalRestServices(externalRestServices);

                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataProvider.getDataSourceContainers());
                    updateDataProvider(dataProvider, dataProviderId);
                    return newDataSource;

                }
                else{
                    throw new ObjectNotFoundException(dataProviderId);
                }
            }
            else{
                throw new InvalidArgumentsException("id");
            }
        }
        else{
            throw new AlreadyExistsException(id);
        }
    }


    /**
     * createDataSourceZ3950Timestamp
     * @param dataProviderId
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param address
     * @param port
     * @param database
     * @param user
     * @param password
     * @param recordSyntax
     * @param charset
     * @param earliestTimestampString
     * @param recordIdPolicyClass
     * @param idXpath
     * @param metadataTransformations
     * @return MessageType
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public DataSource createDataSourceZ3950Timestamp(String dataProviderId, String id, String description, String schema, String namespace,
                                                     String address, String port, String database, String user, String password,
                                                     String recordSyntax, String charset, String earliestTimestampString,
                                                     String recordIdPolicyClass, String idXpath,  Map<String, String> namespaces,
                                                     Map<String, MetadataTransformation> metadataTransformations,
                                                     List<ExternalRestService> externalRestServices) throws DocumentException, IOException, ParseException, AlreadyExistsException, ObjectNotFoundException, InvalidArgumentsException, SQLException {
        if(getDataSourceContainer(id) == null){
            if(isIdValid(id)){
                DataProvider dataProvider = getDataProvider(dataProviderId);

                if(dataProvider != null){
                    if(charset.equals("")){
                        throw new InvalidArgumentsException("charset");
                    }
                    CharacterEncoding targetCharacterEncoding = CharacterEncoding.get(charset);
                    Target target = new Target(address, Integer.valueOf(port), database, user, password, targetCharacterEncoding, recordSyntax);

                    HarvestMethod harvestMethod;
                    try{
                        harvestMethod = new TimestampHarvester(target, DateUtil.string2Date(earliestTimestampString, "yyyyMMdd"));
                    }
                    catch (ParseException e){
                        throw new InvalidArgumentsException("earliestTimestamp");
                    }

                    RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                    DataSource newDataSource = new DataSourceZ3950(dataProvider, id, description, schema, namespace,
                            harvestMethod, recordIdPolicy, new TreeMap<String, MetadataTransformation>());

                    DataSourceContainerDefault DataSourceContainerDefault = new DataSourceContainerDefault(newDataSource);
                    dataProvider.getDataSourceContainers().put(newDataSource.getId(), DataSourceContainerDefault);
                    newDataSource.initAccessPoints();
                    newDataSource.setMetadataTransformations(metadataTransformations);
                    newDataSource.setExternalRestServices(externalRestServices);

                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataProvider.getDataSourceContainers());
                    updateDataProvider(dataProvider, dataProviderId);
                    return newDataSource;

                }
                else{
                    throw new ObjectNotFoundException(dataProviderId);
                }
            }
            else{
                throw new InvalidArgumentsException("id");
            }
        }
        else{
            throw new AlreadyExistsException(id);
        }
    }


    /**
     * createDataSourceZ3950IdSequence
     * @param dataProviderId
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param address
     * @param port
     * @param database
     * @param user
     * @param password
     * @param recordSyntax
     * @param charset
     * @param maximumIdString
     * @param recordIdPolicyClass
     * @param idXpath
     * @param metadataTransformations
     * @return MessageType
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public DataSource createDataSourceZ3950IdSequence(String dataProviderId, String id, String description, String schema, String namespace,
                                                      String address, String port, String database, String user, String password,
                                                      String recordSyntax, String charset, String maximumIdString,
                                                      String recordIdPolicyClass, String idXpath, Map<String, String> namespaces,
                                                      Map<String, MetadataTransformation> metadataTransformations,
                                                      List<ExternalRestService> externalRestServices) throws DocumentException, IOException, ParseException, AlreadyExistsException, ObjectNotFoundException, InvalidArgumentsException, SQLException {
        if(getDataSourceContainer(id) == null){
            if(isIdValid(id)){
                DataProvider dataProvider = getDataProvider(dataProviderId);

                if(dataProvider != null){
                    if(charset.equals("")){
                        throw new InvalidArgumentsException("charset");
                    }
                    CharacterEncoding targetCharacterEncoding = CharacterEncoding.get(charset);
                    Target target = new Target(address, Integer.valueOf(port), database, user, password, targetCharacterEncoding, recordSyntax);

                    Long maximumId = (maximumIdString != null && !maximumIdString.isEmpty() ? Long.valueOf(maximumIdString) : null);
                    HarvestMethod harvestMethod = new IdSequenceHarvester(target, maximumId);

                    RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                    DataSource newDataSource = new DataSourceZ3950(dataProvider, id, description, schema, namespace,
                            harvestMethod, recordIdPolicy, new TreeMap<String, MetadataTransformation>());

                    DataSourceContainerDefault DataSourceContainerDefault = new DataSourceContainerDefault(newDataSource);
                    dataProvider.getDataSourceContainers().put(newDataSource.getId(), DataSourceContainerDefault);
                    newDataSource.initAccessPoints();
                    newDataSource.setMetadataTransformations(metadataTransformations);
                    newDataSource.setExternalRestServices(externalRestServices);

                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataProvider.getDataSourceContainers());
                    updateDataProvider(dataProvider, dataProviderId);
                    return newDataSource;

                }
                else{
                    throw new ObjectNotFoundException(dataProviderId);
                }
            }
            else{
                throw new InvalidArgumentsException("id");
            }
        }
        else{
            throw new AlreadyExistsException(id);
        }
    }

    /**
     * createDataSourceFtp
     * @param dataProviderId
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param isoFormat
     * @param charset
     * @param recordIdPolicyClass
     * @param idXpath
     * @param recordXPath
     * @param server
     * @param user
     * @param password
     * @param ftpPath
     * @param metadataTransformations
     * @return MessageType
     * @throws DocumentException
     * @throws IOException
     */
    public DataSource createDataSourceFtp(String dataProviderId, String id, String description, String schema, String namespace,
                                          String metadataFormat, String isoFormat, String charset,
                                          String recordIdPolicyClass, String idXpath, Map<String, String> namespaces,
                                          String recordXPath, String server, String user, String password, String ftpPath,
                                          Map<String, MetadataTransformation> metadataTransformations,
                                          List<ExternalRestService> externalRestServices, String marcFormat) throws DocumentException, IOException, AlreadyExistsException, ObjectNotFoundException, InvalidArgumentsException, SQLException {
        if(getDataSourceContainer(id) == null){
            if(isIdValid(id)){
                DataProvider dataProvider = getDataProvider(dataProviderId);

                if(dataProvider != null){
                    String accessType;
                    if(user.equals("") && password.equals("")){
                        accessType = DataSourceFtp.ANONYMOUS;
                    } else {
                        accessType = DataSourceFtp.NORMAL;
                    }

                    if(ftpPath.equals(""))
                        throw new InvalidArgumentsException("ftpPath");

                    FileRetrieveStrategy retrieveStrategy = new DataSourceFtp(server, user, password, accessType, ftpPath);

                    RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                    if(recordIdPolicy != null){
                        CharacterEncoding characterEncoding = null;
                        FileExtractStrategy extractStrategy = DataSourceUtil.extractStrategyString(metadataFormat, isoFormat);
                        if(extractStrategy.getClass() == Iso2709FileExtract.class) {
                            if(charset.equals("")){
                                throw new InvalidArgumentsException("charset");
                            }
                            characterEncoding = CharacterEncoding.get(charset);
                        }
                        else if(extractStrategy.getClass() == MarcXchangeFileExtract.class) {
                        }
                        else if(extractStrategy.getClass() == SimpleFileExtract.class) {
                        }

                        DataSource newDataSource = new DataSourceDirectoryImporter(dataProvider, id, description, schema, namespace, metadataFormat, extractStrategy,
                                retrieveStrategy, characterEncoding, DataSourceFtp.getOutputFtpPath(server, id), recordIdPolicy, new TreeMap<String, MetadataTransformation>(), recordXPath, namespaces);

                        DataSourceContainerDefault DataSourceContainerDefault = new DataSourceContainerDefault(newDataSource);
                        dataProvider.getDataSourceContainers().put(newDataSource.getId(), DataSourceContainerDefault);
                        newDataSource.initAccessPoints();
                        newDataSource.setMetadataTransformations(metadataTransformations);
                        newDataSource.setExternalRestServices(externalRestServices);
                        newDataSource.setMarcFormat(marcFormat);

                        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataProvider.getDataSourceContainers());
                        updateDataProvider(dataProvider, dataProviderId);
                        return newDataSource;

                    }
                    else{
                        throw new InvalidArgumentsException("recordIdPolicy");
                    }
                }
                else{
                    throw new ObjectNotFoundException(dataProviderId);
                }
            }
            else{
                throw new InvalidArgumentsException("id");
            }
        }
        else{
            throw new AlreadyExistsException(id);
        }
    }

    /**
     * createDataSourceHttp
     * @param dataProviderId
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param isoFormat
     * @param charset
     * @param recordIdPolicyClass
     * @param idXpath
     * @param recordXPath
     * @param url
     * @param metadataTransformations
     * @return MessageType
     * @throws DocumentException
     * @throws IOException
     */
    public DataSource createDataSourceHttp(String dataProviderId, String id, String description, String schema, String namespace,
                                           String metadataFormat, String isoFormat, String charset,
                                           String recordIdPolicyClass, String idXpath, Map<String, String> namespaces,
                                           String recordXPath, String url, Map<String, MetadataTransformation> metadataTransformations,
                                           List<ExternalRestService> externalRestServices, String marcFormat) throws DocumentException, IOException, AlreadyExistsException, ObjectNotFoundException, InvalidArgumentsException, SQLException {
        if(getDataSourceContainer(id) == null){
            if(isIdValid(id)){
                DataProvider dataProvider = getDataProvider(dataProviderId);

                if(dataProvider != null){
                    FileRetrieveStrategy retrieveStrategy = new DataSourceHttp(url);

                    RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                    if(recordIdPolicy != null){
                        CharacterEncoding characterEncoding = null;
                        FileExtractStrategy extractStrategy = DataSourceUtil.extractStrategyString(metadataFormat, isoFormat);
                        if(extractStrategy.getClass() == Iso2709FileExtract.class) {
                            if(charset.equals("")){
                                throw new InvalidArgumentsException("charset");
                            }
                            characterEncoding = CharacterEncoding.get(charset);
                        }
                        else if(extractStrategy.getClass() == MarcXchangeFileExtract.class) {
                        }
                        else if(extractStrategy.getClass() == SimpleFileExtract.class) {
                        }

                        if(url.equals("")  || !FileUtil.checkUrl(url))
                            throw new InvalidArgumentsException("url");

                        DataSource newDataSource = new DataSourceDirectoryImporter(dataProvider, id, description, schema, namespace, metadataFormat, extractStrategy,
                                retrieveStrategy, characterEncoding, DataSourceHttp.getOutputHttpPath(url, id), recordIdPolicy, new TreeMap<String, MetadataTransformation>(), recordXPath, namespaces);

                        DataSourceContainerDefault DataSourceContainerDefault = new DataSourceContainerDefault(newDataSource);
                        dataProvider.getDataSourceContainers().put(newDataSource.getId(), DataSourceContainerDefault);
                        newDataSource.initAccessPoints();
                        newDataSource.setMetadataTransformations(metadataTransformations);
                        newDataSource.setExternalRestServices(externalRestServices);
                        newDataSource.setMarcFormat(marcFormat);

                        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataProvider.getDataSourceContainers());
                        updateDataProvider(dataProvider, dataProviderId);
                        return newDataSource;

                    }
                    else{
                        throw new InvalidArgumentsException("recordIdPolicy");
                    }
                }
                else{
                    throw new ObjectNotFoundException(dataProviderId);
                }
            }
            else{
                throw new InvalidArgumentsException("id");
            }
        }
        else{
            throw new AlreadyExistsException(id);
        }
    }

    /**
     * createDataSourceFolder
     * @param dataProviderId
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param isoVariant
     * @param charset
     * @param recordIdPolicyClass
     * @param idXpath
     * @param recordXPath
     * @param sourcesDirPath
     * @param metadataTransformations
     * @return MessageType
     * @throws DocumentException
     * @throws IOException
     */
    public DataSource createDataSourceFolder(String dataProviderId, String id, String description, String schema, String namespace,
                                             String metadataFormat, String isoVariant, String charset,
                                             String recordIdPolicyClass, String idXpath, Map<String, String> namespaces,
                                             String recordXPath, String sourcesDirPath, Map<String, MetadataTransformation> metadataTransformations,
                                             List<ExternalRestService> externalRestServices, String marcFormat) throws DocumentException, IOException, ObjectNotFoundException, AlreadyExistsException, InvalidArgumentsException, SQLException {
        if(getDataSourceContainer(id) == null){
            if(isIdValid(id)){
                DataProvider dataProvider = getDataProvider(dataProviderId);

                if(dataProvider != null){
                    FileRetrieveStrategy retrieveStrategy = new DataSourceFolder();

                    RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                    if(recordIdPolicy != null){
                        CharacterEncoding characterEncoding = null;
                        FileExtractStrategy extractStrategy = DataSourceUtil.extractStrategyString(metadataFormat, isoVariant);
                        if(extractStrategy.getClass() == Iso2709FileExtract.class) {
                            if(charset.equals("")){
                                throw new InvalidArgumentsException("charset");
                            }
                            characterEncoding = CharacterEncoding.get(charset);

                        }
                        else if(extractStrategy.getClass() == MarcXchangeFileExtract.class) {
                        }
                        else if(extractStrategy.getClass() == SimpleFileExtract.class) {
                        }


                        if(sourcesDirPath.equals(""))
                            throw new InvalidArgumentsException("folderPath");

                        DataSource newDataSource = new DataSourceDirectoryImporter(dataProvider, id, description, schema, namespace, metadataFormat, extractStrategy,
                                retrieveStrategy, characterEncoding, sourcesDirPath, recordIdPolicy, new TreeMap<String, MetadataTransformation>(), recordXPath, new HashMap<String, String>());

                        DataSourceContainerDefault DataSourceContainerDefault = new DataSourceContainerDefault(newDataSource);
                        dataProvider.getDataSourceContainers().put(newDataSource.getId(), DataSourceContainerDefault);
                        newDataSource.initAccessPoints();
                        newDataSource.setMetadataTransformations(metadataTransformations);
                        newDataSource.setExternalRestServices(externalRestServices);
                        newDataSource.setMarcFormat(marcFormat);

                        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().initialize(dataProvider.getDataSourceContainers());
                        updateDataProvider(dataProvider, dataProviderId);
                        return newDataSource;
                    }
                    else{
                        throw new InvalidArgumentsException("recordIdPolicy");
                    }
                }
                else{
                    throw new ObjectNotFoundException(dataProviderId);
                }
            }
            else{
                throw new InvalidArgumentsException("id");
            }
        }
        else{
            throw new AlreadyExistsException(id);
        }
    }

    public DataSource updateDataSourceSruRecordUpdate(String oldId, String id, String description, String schema, String namespace,
            String metadataFormat, 
            Map<String, MetadataTransformation> metadataTransformations,
            List<ExternalRestService> externalRestServices,
            String marcFormat, boolean useLastIngestDate) throws DocumentException, IOException, ObjectNotFoundException, InvalidArgumentsException, IncompatibleInstanceException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(oldId);
        if(dataSourceContainer != null){
            DataSource dataSource = dataSourceContainer.getDataSource();
            if(!isIdValid(id))
                throw new InvalidArgumentsException("id");
            
                DataProvider dataProviderParent = getDataProviderParent(oldId);
                if(dataProviderParent != null){
                    if(!(dataSource instanceof DataSourceOai)){
                        DataSource newDataSource = new DataSourceSruRecordUpdate(dataProviderParent, id, description, schema, namespace, metadataFormat,
                                new IdGenerated(), new TreeMap<String, MetadataTransformation>());
                        newDataSource.setAccessPoints(dataSource.getAccessPoints());
                        newDataSource.setStatus(dataSource.getStatus());
                        
                        setLastIngestDate(useLastIngestDate, dataSource, newDataSource);
                        newDataSource.setOldTasksList(dataSource.getOldTasksList());
                        newDataSource.setTags(dataSource.getTags());
                        
                        dataProviderParent.getDataSourceContainers().remove(dataSource.getId());
                        dataProviderParent.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                        dataSource = newDataSource;
                    }
                    
                    dataSource.setId(id);
                    dataSource.setDescription(description);
                    dataSource.setSchema(schema);
                    dataSource.setNamespace(namespace);
                    dataSource.setMetadataFormat(metadataFormat);
                    dataSource.setMetadataTransformations(metadataTransformations);
                    dataSource.setExternalRestServices(externalRestServices);
                    dataSource.setMarcFormat(marcFormat);
                    
                    if(!id.equals(oldId)){
                        DataSourceContainerDefault dataSourceContainerDefault = new DataSourceContainerDefault(dataSource);
                        updateDataSourceContainer(dataSourceContainerDefault, oldId);
                    }
                    updateDataProvider(dataProviderParent, dataProviderParent.getId());
                    return dataSource;
                }
                else{
                    throw new ObjectNotFoundException(id);
                }
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }
    public DataSource updateDataSourceOai(String oldId, String id, String description, String schema, String namespace,
                                          String metadataFormat, String oaiSourceURL, String oaiSet,
                                          Map<String, MetadataTransformation> metadataTransformations,
                                          List<ExternalRestService> externalRestServices,
                                          String marcFormat, boolean useLastIngestDate) throws DocumentException, IOException, ObjectNotFoundException, InvalidArgumentsException, IncompatibleInstanceException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(oldId);
        if(dataSourceContainer != null){
            DataSource dataSource = dataSourceContainer.getDataSource();
            if(!isIdValid(id))
                throw new InvalidArgumentsException("id");

            //validate the URL server
            if (!oaiSourceURL.startsWith("http://") &&
                    !oaiSourceURL.startsWith("https://")) {
                oaiSourceURL = "http://" + oaiSourceURL;
            }
            if(new java.net.URL(oaiSourceURL).openConnection().getHeaderField(0) != null && FileUtil.checkUrl(oaiSourceURL)){
                DataProvider dataProviderParent = getDataProviderParent(oldId);
                if(dataProviderParent != null){
                    if(!(dataSource instanceof DataSourceOai)){
                        DataSource newDataSource = new DataSourceOai(dataProviderParent, id, description, schema, namespace, metadataFormat,
                                oaiSourceURL, oaiSet, new IdProvided(), new TreeMap<String, MetadataTransformation>());
                        newDataSource.setAccessPoints(dataSource.getAccessPoints());
                        newDataSource.setStatus(dataSource.getStatus());

                        setLastIngestDate(useLastIngestDate, dataSource, newDataSource);
                        newDataSource.setOldTasksList(dataSource.getOldTasksList());
                        newDataSource.setTags(dataSource.getTags());

                        dataProviderParent.getDataSourceContainers().remove(dataSource.getId());
                        dataProviderParent.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                        dataSource = newDataSource;
                    }

                    dataSource.setId(id);
                    dataSource.setDescription(description);
                    dataSource.setSchema(schema);
                    dataSource.setNamespace(namespace);
                    dataSource.setMetadataFormat(metadataFormat);
                    ((DataSourceOai)dataSource).setOaiSourceURL(oaiSourceURL);
                    ((DataSourceOai)dataSource).setOaiSet(oaiSet);
                    dataSource.setMetadataTransformations(metadataTransformations);
                    dataSource.setExternalRestServices(externalRestServices);
                    dataSource.setMarcFormat(marcFormat);

                    if(!id.equals(oldId)){
                        DataSourceContainerDefault dataSourceContainerDefault = new DataSourceContainerDefault(dataSource);
                        updateDataSourceContainer(dataSourceContainerDefault, oldId);
                    }
                    updateDataProvider(dataProviderParent, dataProviderParent.getId());
                    return dataSource;
                }
                else{
                    throw new ObjectNotFoundException(id);
                }
            }
            else{
                throw new InvalidArgumentsException("oaiURL");
            }
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }

    public DataSource updateDataSourceZ3950Timestamp(String oldId, String id, String description, String schema, String namespace,
                                                     String address, String port, String database, String user, String password,
                                                     String recordSyntax, String charset, String earliestTimestampString,
                                                     String recordIdPolicyClass, String idXpath, Map<String, String> namespaces,
                                                     Map<String, MetadataTransformation> metadataTransformations,
                                                     List<ExternalRestService> externalRestServices,
                                                     boolean useLastIngestDate) throws DocumentException, IOException, ParseException, ObjectNotFoundException, InvalidArgumentsException, IncompatibleInstanceException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(oldId);
        if(dataSourceContainer != null){
            DataSource dataSource = dataSourceContainer.getDataSource();
            if(!isIdValid(id))
                throw new InvalidArgumentsException("id");

            DataProvider dataProviderParent = getDataProviderParent(oldId);
            if(dataProviderParent != null){
                if(charset.equals("")){
                    throw new InvalidArgumentsException("charset");
                }
                CharacterEncoding targetCharacterEncoding = CharacterEncoding.get(charset);
                Target target = new Target(address, Integer.valueOf(port), database, user, password, targetCharacterEncoding, recordSyntax);

                HarvestMethod harvestMethod;
                try{
                    harvestMethod = new TimestampHarvester(target, DateUtil.string2Date(earliestTimestampString, "yyyyMMdd"));
                }
                catch (ParseException e){
                    throw new InvalidArgumentsException("earliestTimestamp");
                }

                RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                if(!(dataSource instanceof DataSourceZ3950)){
                    DataSource newDataSource = new DataSourceZ3950(dataProviderParent, id, description, schema, namespace,
                            harvestMethod, recordIdPolicy, new TreeMap<String, MetadataTransformation>());
                    newDataSource.setAccessPoints(dataSource.getAccessPoints());
                    newDataSource.setStatus(dataSource.getStatus());

                    setLastIngestDate(useLastIngestDate, dataSource, newDataSource);
                    newDataSource.setOldTasksList(dataSource.getOldTasksList());
                    newDataSource.setTags(dataSource.getTags());

                    dataProviderParent.getDataSourceContainers().remove(dataSource.getId());
                    dataProviderParent.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                    dataSource = newDataSource;
                }

                dataSource.setId(id);
                dataSource.setDescription(description);
                dataSource.setSchema(schema);
                dataSource.setNamespace(namespace);
                dataSource.setRecordIdPolicy(recordIdPolicy);
                ((DataSourceZ3950)dataSource).setHarvestMethod(harvestMethod);
                dataSource.setMetadataTransformations(metadataTransformations);
                dataSource.setExternalRestServices(externalRestServices);

                if(!id.equals(oldId)){
                    DataSourceContainerDefault dataSourceContainerDefault = new DataSourceContainerDefault(dataSource);
                    updateDataSourceContainer(dataSourceContainerDefault, oldId);
                }
                updateDataProvider(dataProviderParent, dataProviderParent.getId());
                return dataSource;
            }
            else{
                throw new ObjectNotFoundException(id);
            }
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }

    public DataSource updateDataSourceZ3950IdList(String oldId, String id, String description, String schema, String namespace, String address,
                                                  String port, String database, String user, String password, String recordSyntax,
                                                  String charset, String filePath, String recordIdPolicyClass, String idXpath,
                                                  Map<String, String> namespaces, Map<String, MetadataTransformation> metadataTransformations,
                                                  List<ExternalRestService> externalRestServices,boolean useLastIngestDate) throws DocumentException, IOException, ParseException, ObjectNotFoundException, IncompatibleInstanceException, InvalidArgumentsException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(oldId);
        if(dataSourceContainer != null){
            if(!isIdValid(id))
                throw new InvalidArgumentsException("id");

            DataSource dataSource = dataSourceContainer.getDataSource();
            DataProvider dataProviderParent = getDataProviderParent(oldId);
            if(dataProviderParent != null){
                if(charset.equals("")){
                    throw new InvalidArgumentsException("charset");
                }
                CharacterEncoding targetCharacterEncoding = CharacterEncoding.get(charset);
                Target target = new Target(address, Integer.valueOf(port), database, user, password, targetCharacterEncoding, recordSyntax);

                File file;
                if(!filePath.isEmpty()){
                    try{
                        FileUtils.forceDelete(((IdListHarvester)((DataSourceZ3950) dataSource).getHarvestMethod()).getIdListFile());
                    }
                    catch (Exception e){
                        log.error("Error removing z39.50 file: " + ((IdListHarvester)((DataSourceZ3950) dataSource).getHarvestMethod()).getIdListFile());
                    }
                    file = ((IdListHarvester)((DataSourceZ3950) dataSource).getHarvestMethod()).getIdListFile();
                    FileUtils.copyFile(new File(filePath), file);
                    FileUtils.forceDelete(new File(filePath));
                    ((IdListHarvester)((DataSourceZ3950) dataSource).getHarvestMethod()).setIdListFile(file);
                }
                else{
                    file = ((IdListHarvester)((DataSourceZ3950) dataSource).getHarvestMethod()).getIdListFile();
                }
                HarvestMethod harvestMethod = new IdListHarvester(target, file);

                RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                if(!(dataSource instanceof DataSourceZ3950)){
                    DataSource newDataSource = new DataSourceZ3950(dataProviderParent, id, description, schema, namespace,
                            harvestMethod, recordIdPolicy, new TreeMap<String, MetadataTransformation>());
                    newDataSource.setAccessPoints(dataSource.getAccessPoints());
                    newDataSource.setStatus(dataSource.getStatus());

                    setLastIngestDate(useLastIngestDate, dataSource, newDataSource);
                    newDataSource.setOldTasksList(dataSource.getOldTasksList());
                    newDataSource.setTags(dataSource.getTags());

                    dataProviderParent.getDataSourceContainers().remove(dataSource.getId());
                    dataProviderParent.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                    dataSource = newDataSource;
                }

                dataSource.setId(id);
                dataSource.setDescription(description);
                dataSource.setSchema(schema);
                dataSource.setNamespace(namespace);
                dataSource.setRecordIdPolicy(recordIdPolicy);
                ((DataSourceZ3950)dataSource).setHarvestMethod(harvestMethod);
                dataSource.setMetadataTransformations(metadataTransformations);
                dataSource.setExternalRestServices(externalRestServices);

                if(!id.equals(oldId)){
                    DataSourceContainerDefault dataSourceContainerDefault = new DataSourceContainerDefault(dataSource);
                    updateDataSourceContainer(dataSourceContainerDefault, oldId);
                }
                updateDataProvider(dataProviderParent, dataProviderParent.getId());
                return dataSource;
            }
            else{
                throw new ObjectNotFoundException(id);
            }
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }

    public DataSource updateDataSourceZ3950IdSequence(String oldId, String id, String description, String schema, String namespace,
                                                      String address, String port, String database, String user, String password,
                                                      String recordSyntax, String charset, String maximumIdString,
                                                      String recordIdPolicyClass, String idXpath, Map<String, String> namespaces,
                                                      Map<String, MetadataTransformation> metadataTransformations,
                                                      List<ExternalRestService> externalRestServices,
                                                      boolean useLastIngestDate) throws DocumentException, IOException, ParseException, ObjectNotFoundException, InvalidArgumentsException, IncompatibleInstanceException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(oldId);
        if(dataSourceContainer != null){
            if(!isIdValid(id))
                throw new InvalidArgumentsException("id");

            DataSource dataSource = dataSourceContainer.getDataSource();
            DataProvider dataProviderParent = getDataProviderParent(oldId);
            if(dataProviderParent != null){
                if(charset.equals("")){
                    throw new InvalidArgumentsException("charset");
                }
                CharacterEncoding targetCharacterEncoding = CharacterEncoding.get(charset);
                Target target = new Target(address, Integer.valueOf(port), database, user, password, targetCharacterEncoding, recordSyntax);

                Long maximumId = (maximumIdString != null && !maximumIdString.isEmpty() ? Long.valueOf(maximumIdString) : null);
                HarvestMethod harvestMethod = new IdSequenceHarvester(target, maximumId);

                RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                if(!(dataSource instanceof DataSourceZ3950)){
                    DataSource newDataSource = new DataSourceZ3950(dataProviderParent, id, description, schema, namespace,
                            harvestMethod, recordIdPolicy, new TreeMap<String, MetadataTransformation>());
                    newDataSource.setAccessPoints(dataSource.getAccessPoints());
                    newDataSource.setStatus(dataSource.getStatus());

                    setLastIngestDate(useLastIngestDate, dataSource, newDataSource);
                    newDataSource.setOldTasksList(dataSource.getOldTasksList());
                    newDataSource.setTags(dataSource.getTags());

                    dataProviderParent.getDataSourceContainers().remove(dataSource.getId());
                    dataProviderParent.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                    dataSource = newDataSource;
                }

                dataSource.setId(id);
                dataSource.setDescription(description);
                dataSource.setSchema(schema);
                dataSource.setNamespace(namespace);
                dataSource.setRecordIdPolicy(recordIdPolicy);
                ((DataSourceZ3950)dataSource).setHarvestMethod(harvestMethod);
                dataSource.setMetadataTransformations(metadataTransformations);
                dataSource.setExternalRestServices(externalRestServices);

                if(!id.equals(oldId)){
                    DataSourceContainerDefault dataSourceContainerDefault = new DataSourceContainerDefault(dataSource);
                    updateDataSourceContainer(dataSourceContainerDefault, oldId);
                }
                updateDataProvider(dataProviderParent, dataProviderParent.getId());
                return dataSource;
            }
            else{
                throw new ObjectNotFoundException(id);
            }
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }

    public DataSource updateDataSourceFtp(String oldId, String id, String description, String schema, String namespace,
                                          String metadataFormat, String isoFormat, String charset, String recordIdPolicyClass,
                                          String idXpath, Map<String, String> namespaces, String recordXPath, String server,
                                          String user, String password, String ftpPath,
                                          Map<String, MetadataTransformation> metadataTransformations,
                                          List<ExternalRestService> externalRestServices, String marcFormat,
                                          boolean useLastIngestDate) throws DocumentException, IOException,
            ObjectNotFoundException, InvalidArgumentsException, IncompatibleInstanceException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(oldId);
        if(dataSourceContainer != null){
            DataSource dataSource = dataSourceContainer.getDataSource();
            if(!isIdValid(id))
                throw new InvalidArgumentsException("id");
            if(ftpPath.equals(""))
                throw new InvalidArgumentsException("ftpPath");

            DataProvider dataProviderParent = getDataProviderParent(oldId);
            if(dataProviderParent != null){
                String accessType;
                if(user.equals("") && password.equals("")){
                    accessType = DataSourceFtp.ANONYMOUS;
                }
                else{
                    accessType = DataSourceFtp.NORMAL;
                }

                FileRetrieveStrategy retrieveStrategy = new DataSourceFtp(server, user, password, accessType, ftpPath);

                RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                if(recordIdPolicy != null){
                    CharacterEncoding characterEncoding = null;
                    FileExtractStrategy extractStrategy = DataSourceUtil.extractStrategyString(metadataFormat, isoFormat);
                    if(extractStrategy.getClass() == Iso2709FileExtract.class) {
                        if(charset.equals("")){
                            throw new InvalidArgumentsException("charset");
                        }
                        characterEncoding = CharacterEncoding.get(charset);
                    }
                    else if(extractStrategy.getClass() == MarcXchangeFileExtract.class) {
                    }
                    else if(extractStrategy.getClass() == SimpleFileExtract.class) {
                    }

                    if(!(dataSource instanceof DataSourceDirectoryImporter)){
                        DataSource newDataSource = new DataSourceDirectoryImporter(dataProviderParent, id, description, schema, namespace, metadataFormat, extractStrategy,
                                retrieveStrategy, characterEncoding, DataSourceFtp.getOutputFtpPath(server, id), recordIdPolicy, new TreeMap<String, MetadataTransformation>(), recordXPath, new HashMap<String, String>());
                        newDataSource.setAccessPoints(dataSource.getAccessPoints());
                        newDataSource.setStatus(dataSource.getStatus());

                        setLastIngestDate(useLastIngestDate, dataSource, newDataSource);
                        newDataSource.setOldTasksList(dataSource.getOldTasksList());
                        newDataSource.setTags(dataSource.getTags());

                        dataProviderParent.getDataSourceContainers().remove(dataSource.getId());
                        dataProviderParent.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                        dataSource = newDataSource;
                    }

                    dataSource.setId(id);
                    dataSource.setDescription(description);
                    dataSource.setSchema(schema);
                    dataSource.setNamespace(namespace);
                    dataSource.setMetadataFormat(metadataFormat);
                    dataSource.setRecordIdPolicy(recordIdPolicy);
                    ((DataSourceDirectoryImporter)dataSource).setExtractStrategy(extractStrategy);
                    ((DataSourceDirectoryImporter)dataSource).setRetrieveStrategy(retrieveStrategy);
                    ((DataSourceDirectoryImporter)dataSource).setCharacterEncoding(characterEncoding);
                    ((DataSourceDirectoryImporter)dataSource).setSourcesDirPath(DataSourceFtp.getOutputFtpPath(server, id));
                    ((DataSourceDirectoryImporter)dataSource).setRecordXPath(recordXPath);
                    ((DataSourceDirectoryImporter)dataSource).setNamespaces(new HashMap<String, String>());
                    dataSource.setMetadataTransformations(metadataTransformations);
                    dataSource.setExternalRestServices(externalRestServices);
                    dataSource.setMarcFormat(marcFormat);

                    if(!id.equals(oldId)){
                        DataSourceContainerDefault dataSourceContainerDefault = new DataSourceContainerDefault(dataSource);
                        updateDataSourceContainer(dataSourceContainerDefault, oldId);
                    }
                    updateDataProvider(dataProviderParent, dataProviderParent.getId());
                    return dataSource;
                }
                else{
                    throw new InvalidArgumentsException("recordIdPolicy");
                }
            }
            else{
                throw new ObjectNotFoundException(id);
            }
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }

    public DataSource updateDataSourceHttp(String oldId, String id, String description, String schema, String namespace, String metadataFormat,
                                           String isoFormat, String charset, String recordIdPolicyClass, String idXpath,
                                           Map<String, String> namespaces, String recordXPath, String url,
                                           Map<String, MetadataTransformation> metadataTransformations,
                                           List<ExternalRestService> externalRestServices, String marcFormat,
                                           boolean useLastIngestDate) throws DocumentException, IOException, ObjectNotFoundException, InvalidArgumentsException, IncompatibleInstanceException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(oldId);
        if(dataSourceContainer != null){
            DataSource dataSource = dataSourceContainer.getDataSource();
            if(!isIdValid(id))
                throw new InvalidArgumentsException("id");
            if(url.equals(""))
                throw new InvalidArgumentsException("url");


            DataProvider dataProviderParent = getDataProviderParent(oldId);
            if(dataProviderParent != null){
                RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                if(recordIdPolicy != null){
                    CharacterEncoding characterEncoding = null;
                    FileExtractStrategy extractStrategy = DataSourceUtil.extractStrategyString(metadataFormat, isoFormat);
                    if(extractStrategy.getClass() == Iso2709FileExtract.class) {
                        if(charset.equals("")){
                            throw new InvalidArgumentsException("charset");
                        }
                        characterEncoding = CharacterEncoding.get(charset);
                    }
                    else if(extractStrategy.getClass() == MarcXchangeFileExtract.class) {
                    }
                    else if(extractStrategy.getClass() == SimpleFileExtract.class) {
                    }

                    FileRetrieveStrategy retrieveStrategy = new DataSourceHttp(url);

                    if(!(dataSource instanceof DataSourceDirectoryImporter)){
                        DataSource newDataSource = new DataSourceDirectoryImporter(dataProviderParent, id, description, schema, namespace, metadataFormat, extractStrategy,
                                retrieveStrategy, characterEncoding, DataSourceHttp.getOutputHttpPath(url, id), recordIdPolicy, new TreeMap<String, MetadataTransformation>(), recordXPath, new HashMap<String, String>());
                        newDataSource.setAccessPoints(dataSource.getAccessPoints());
                        newDataSource.setStatus(dataSource.getStatus());

                        setLastIngestDate(useLastIngestDate, dataSource, newDataSource);
                        newDataSource.setOldTasksList(dataSource.getOldTasksList());
                        newDataSource.setTags(dataSource.getTags());

                        dataProviderParent.getDataSourceContainers().remove(dataSource.getId());
                        dataProviderParent.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                        dataSource = newDataSource;
                    }

                    dataSource.setId(id);
                    dataSource.setDescription(description);
                    dataSource.setSchema(schema);
                    dataSource.setNamespace(namespace);
                    dataSource.setMetadataFormat(metadataFormat);
                    dataSource.setRecordIdPolicy(recordIdPolicy);
                    ((DataSourceDirectoryImporter)dataSource).setExtractStrategy(extractStrategy);
                    ((DataSourceDirectoryImporter)dataSource).setRetrieveStrategy(new DataSourceHttp(url));
                    ((DataSourceDirectoryImporter)dataSource).setCharacterEncoding(characterEncoding);
                    ((DataSourceDirectoryImporter)dataSource).setSourcesDirPath(DataSourceHttp.getOutputHttpPath(url, id));
                    ((DataSourceDirectoryImporter)dataSource).setRecordXPath(recordXPath);
                    ((DataSourceDirectoryImporter)dataSource).setNamespaces(new HashMap<String, String>());
                    dataSource.setMetadataTransformations(metadataTransformations);
                    dataSource.setExternalRestServices(externalRestServices);
                    dataSource.setMarcFormat(marcFormat);

                    if(!id.equals(oldId)){
                        DataSourceContainerDefault dataSourceContainerDefault = new DataSourceContainerDefault(dataSource);
                        updateDataSourceContainer(dataSourceContainerDefault, oldId);
                    }
                    updateDataProvider(dataProviderParent, dataProviderParent.getId());
                    return dataSource;
                }
                else{
                    throw new InvalidArgumentsException("recordIdPolicy");
                }
            }
            else{
                throw new ObjectNotFoundException(id);
            }
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }

    public DataSource updateDataSourceFolder(String oldId, String id, String description, String schema, String namespace, String metadataFormat,
                                             String isoFormat, String charset, String recordIdPolicyClass, String idXpath,
                                             Map<String, String> namespaces, String recordXPath, String sourcesDirPath,
                                             Map<String, MetadataTransformation> metadataTransformations,
                                             List<ExternalRestService> externalRestServices, String marcFormat,
                                             boolean useLastIngestDate) throws IOException, DocumentException, ObjectNotFoundException, InvalidArgumentsException, IncompatibleInstanceException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(oldId);
        if(dataSourceContainer != null){
            DataSource dataSource = dataSourceContainer.getDataSource();
            DataProvider dataProviderParent = getDataProviderParent(oldId);
            if(dataProviderParent != null){
                if(!isIdValid(id))
                    throw new InvalidArgumentsException("id");

                if(sourcesDirPath.equals(""))
                    throw new InvalidArgumentsException("folderPath");

                RecordIdPolicy recordIdPolicy = DataSourceUtil.createIdPolicy(recordIdPolicyClass, idXpath, namespaces);

                if(recordIdPolicy != null){
                    CharacterEncoding characterEncoding = null;
                    FileExtractStrategy extractStrategy = DataSourceUtil.extractStrategyString(metadataFormat, isoFormat);
                    if(extractStrategy.getClass() == Iso2709FileExtract.class) {
                        if(charset.equals("")){
                            throw new InvalidArgumentsException("charset");
                        }
                        characterEncoding = CharacterEncoding.get(charset);
                    }
                    else if(extractStrategy.getClass() == MarcXchangeFileExtract.class) {
                    }
                    else if(extractStrategy.getClass() == SimpleFileExtract.class) {
                    }

                    FileRetrieveStrategy retrieveStrategy = new DataSourceFolder();

                    if(!(dataSource instanceof DataSourceDirectoryImporter)){
                        DataSource newDataSource = new DataSourceDirectoryImporter(dataProviderParent, id, description, schema, namespace, metadataFormat, extractStrategy,
                                retrieveStrategy, characterEncoding, sourcesDirPath, recordIdPolicy, new TreeMap<String, MetadataTransformation>(), recordXPath, new HashMap<String, String>());
                        newDataSource.setAccessPoints(dataSource.getAccessPoints());
                        newDataSource.setStatus(dataSource.getStatus());

                        setLastIngestDate(useLastIngestDate, dataSource, newDataSource);
                        newDataSource.setOldTasksList(dataSource.getOldTasksList());
                        newDataSource.setTags(dataSource.getTags());

                        dataProviderParent.getDataSourceContainers().remove(dataSource.getId());
                        dataProviderParent.getDataSourceContainers().put(newDataSource.getId(), new DataSourceContainerDefault(newDataSource));
                        dataSource = newDataSource;
                    }

                    dataSource.setId(id);
                    dataSource.setDescription(description);
                    dataSource.setSchema(schema);
                    dataSource.setNamespace(namespace);
                    dataSource.setMetadataFormat(metadataFormat);
                    dataSource.setRecordIdPolicy(recordIdPolicy);
                    ((DataSourceDirectoryImporter)dataSource).setExtractStrategy(extractStrategy);
                    ((DataSourceDirectoryImporter)dataSource).setCharacterEncoding(characterEncoding);
                    ((DataSourceDirectoryImporter)dataSource).setSourcesDirPath(sourcesDirPath);
                    ((DataSourceDirectoryImporter)dataSource).setRecordXPath(recordXPath);
                    ((DataSourceDirectoryImporter)dataSource).setNamespaces(new HashMap<String, String>());
                    dataSource.setMetadataTransformations(metadataTransformations);
                    dataSource.setExternalRestServices(externalRestServices);
                    dataSource.setMarcFormat(marcFormat);

                    if(!id.equals(oldId)){
                        DataSourceContainerDefault dataSourceContainerDefault = new DataSourceContainerDefault(dataSource);
                        updateDataSourceContainer(dataSourceContainerDefault, oldId);
                    }
                    updateDataProvider(dataProviderParent, dataProviderParent.getId());
                    return dataSource;
                }
                else{
                    throw new InvalidArgumentsException("recordIdPolicy");
                }
            }
            else{
                throw new ObjectNotFoundException(id);
            }
        }
        else{
            throw new ObjectNotFoundException(id);
        }
    }


    /**
     * Gets the DataSource with id dataSourceId
     *
     * @param dataSourceId
     * @return DataSource with id dataSourceId if exists, null otherwise
     * @throws DocumentException
     * @throws IOException
     */
    public synchronized DataSource getDataSource(String dataSourceId) throws DocumentException, IOException {
        for (DataProvider currentDataProvider : dataProviders) {
            DataSource dataSource = currentDataProvider.getDataSource(dataSourceId);
            if (dataSource != null) {
                return dataSource;
            }
        }
        return null;
    }


    /**
     * Gets the DataProvider parent with id dataSourceId
     *
     * @param dataSourceId
     * @return DataSource with id dataSourceId if exists, null otherwise
     * @throws DocumentException
     * @throws IOException
     */
    public synchronized DataProvider getDataProviderParent(String dataSourceId) {
        for (DataProvider currentDataProvider : dataProviders) {
            for (DataSourceContainer dataSourceContainer : currentDataProvider.getDataSourceContainers().values()) {
                if(dataSourceContainer.getDataSource().getId().equals(dataSourceId)){
                    return currentDataProvider;
                }
            }
        }
        return null;
    }

    /**
     * Moves the DataSource to another parent DataProvider
     *
     * @param newDataProviderID
     * @return Success or failure of moving the data source
     */
    public boolean moveDataSource(String newDataProviderID, String idDataSource2Move) throws IOException, DocumentException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(idDataSource2Move);
        DataProvider dataProviderParent = getDataProviderParent(dataSourceContainer.getDataSource().getId());

        if(dataProviderParent.getId().equals(newDataProviderID)){
            return false;
        }

        for (DataProvider currentDataProvider : dataProviders) {
            if(currentDataProvider.getId().equals(newDataProviderID)){
                currentDataProvider.getDataSourceContainers().put(dataSourceContainer.getDataSource().getId(),dataSourceContainer);
                dataProviderParent.getDataSourceContainers().remove(dataSourceContainer.getDataSource().getId());

                saveData();

                return true;
            }
        }
        return false;
    }

    public void setDataSetSampleState(boolean isSample, DataSource dataSource){
        dataSource.setIsSample(isSample);
        try {
            saveData();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    /**
     * Updates the Data Source with id newDataSourceId from oldDataSourceId.
     *
     * @param oldDataSourceId, newDataSourceId
     * @return true if deletion was completely successful, false otherwise.
     * @throws DocumentException
     */
    public void updateDataSourceId(String oldDataSourceId, String newDataSourceId) throws IOException, DocumentException, SQLException {
        DataSource dataSource = getDataSource(oldDataSourceId);
        // dataSource.initAccessPoints();

        log.info("Updating Data Source with id " + oldDataSourceId + " to id " + newDataSourceId);
        AccessPointsManagerDefault accessPointsManager = (AccessPointsManagerDefault)ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager();

        //Update Access Points
        AccessPoint defaultTimestampAP = dataSource.getAccessPoints().get(AccessPoint.PREFIX_INTERNAL_BD + oldDataSourceId + AccessPoint.SUFIX_TIMESTAMP_INTERNAL_BD);
        accessPointsManager.updateDataSourceAccessPoint(dataSource, defaultTimestampAP.typeOfIndex(),
                AccessPoint.PREFIX_INTERNAL_BD + oldDataSourceId + AccessPoint.SUFIX_TIMESTAMP_INTERNAL_BD, AccessPoint.PREFIX_INTERNAL_BD + newDataSourceId + AccessPoint.SUFIX_TIMESTAMP_INTERNAL_BD);
        log.info("Updated AccessPoint with id " + AccessPoint.PREFIX_INTERNAL_BD + oldDataSourceId + AccessPoint.SUFIX_TIMESTAMP_INTERNAL_BD + " to " + AccessPoint.PREFIX_INTERNAL_BD + newDataSourceId + AccessPoint.SUFIX_TIMESTAMP_INTERNAL_BD);

        AccessPoint defaultRecordAP = dataSource.getAccessPoints().get(AccessPoint.PREFIX_INTERNAL_BD + oldDataSourceId + AccessPoint.SUFIX_RECORD_INTERNAL_BD);
        accessPointsManager.updateDataSourceAccessPoint(dataSource, defaultRecordAP.typeOfIndex(),
                AccessPoint.PREFIX_INTERNAL_BD + oldDataSourceId + AccessPoint.SUFIX_RECORD_INTERNAL_BD, AccessPoint.PREFIX_INTERNAL_BD + newDataSourceId + AccessPoint.SUFIX_RECORD_INTERNAL_BD);
        log.info("Updated AccessPoint with id " + AccessPoint.PREFIX_INTERNAL_BD + oldDataSourceId + AccessPoint.SUFIX_RECORD_INTERNAL_BD + " to " + AccessPoint.PREFIX_INTERNAL_BD + newDataSourceId + AccessPoint.SUFIX_RECORD_INTERNAL_BD);


        // update access points
        dataSource.getAccessPoints().clear();
        dataSource.initAccessPoints();

        // update dataSet info
        dataSource.setId(newDataSourceId);

        //Rename Folder
        dataSource.renameDataSourceDir(oldDataSourceId, newDataSourceId);
        log.info("Renamed Data Source (with new Id " + newDataSourceId + ") Repository Dir");

        //Update Record Counts cache
        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getRecordCountManager().renameDataSourceCounts(oldDataSourceId, newDataSourceId);

        //Update Scheduled Tasks
        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().updateDataSourceTasks(oldDataSourceId, newDataSourceId);
    }

    public synchronized MessageType removeTransformationFromDataSource(String transformationId) {
        try {
            HashMap<String, DataSourceContainer> dataSourceContainers = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getDataManager().loadDataSourceContainers();

            for(DataSourceContainer dataSourceContainer : dataSourceContainers.values()) {
                DataSource currentDataSource = dataSourceContainer.getDataSource();
                MetadataTransformation metadataTransformation = currentDataSource.getMetadataTransformations().get(transformationId);
                if(metadataTransformation != null) {
                    currentDataSource.getMetadataTransformations().remove(metadataTransformation.getId());
                }
            }
            saveData();
            return MessageType.OK;
        } catch (DocumentException e) {
            return MessageType.OTHER;
        } catch (IOException e) {
            return MessageType.OTHER;
        }
    }

    public synchronized MessageType deleteDataSource(String dataSourceId) {
        Iterator<DataProvider> dataProviderIterator = dataProviders.iterator();
        while (dataProviderIterator.hasNext()) {
            DataProvider currentDataProvider = dataProviderIterator.next();

            DataSourceContainer dataSourceContainer = currentDataProvider.getDataSourceContainers().get(dataSourceId);

            if(dataSourceContainer != null){

                DataSource currentDataSource = dataSourceContainer.getDataSource();

                log.info("Deleting Data Source with id " + currentDataSource.getId());

                // remove files from z39.50 - IdListHarvester
                File idListFilePermanent;
                if((currentDataSource instanceof DataSourceZ3950) &&
                        (((DataSourceZ3950)currentDataSource).getHarvestMethod() instanceof IdListHarvester)){
                    String pathFile = ((IdListHarvester)((DataSourceZ3950)currentDataSource).getHarvestMethod()).getIdListFile().getAbsolutePath();
                    idListFilePermanent = new File(pathFile);
                    try{
                        FileUtils.forceDelete(idListFilePermanent);
                    }
                    catch (IOException e) {
                        log.error("Error deleting the file: " + idListFilePermanent.getAbsolutePath());
                    }
                }


                //Delete AccessPoints
                for (AccessPoint accessPoint : currentDataSource.getAccessPoints().values()) {
                    try {
                        ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().deleteIndex(accessPoint);
                        log.info("Deleted AccessPoint with id " + accessPoint.getId());
                    } catch (Exception e) {
                        log.error("Unable to delete Table from Database: " + accessPoint.getId(), e);
                        return MessageType.OTHER;
                    }
                }

                //Delete repository dir
                try {
                    File dataSourceDir = currentDataSource.getDataSourceDir();
                    if(dataSourceDir.exists()) {
                        try{
                            FileUtils.deleteDirectory(dataSourceDir);
                            log.info("Deleted Data Source dir with success from Data Source with id " + dataSourceId);
                        }
                        catch (IOException e){
                            log.error("Unable to delete Data Source dir from Data Source with id " + dataSourceId);
                        }
                    }
                } catch (Exception e) {
                    log.error("Unable to delete Data Source dir from Data Source with id " + dataSourceId);
                    return MessageType.OTHER;
                }

                try {
                    //Delete Record Counts cache
                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getRecordCountManager().removeDataSourceCounts(dataSourceId);

                    //Delete Scheduled Tasks
                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().deleteDataSourceTasks(dataSourceId);

                    // Delete Old Tasks
                    removeOldTasks(dataSourceId);

                    // todo test if it's removing...
                    currentDataProvider.getDataSourceContainers().remove(dataSourceId);

                    saveData();
                    return MessageType.OK;
                } catch (Exception e) {
                    log.error("Can't delete Data Source with id " + dataSourceId + ".");
                    return MessageType.OTHER;
                }
            }
        }
        return MessageType.NOT_FOUND;
    }

    /**
     * Start the data source ingestion
     * @param dataSourceId
     * @param fullIngest
     * @throws DocumentException
     * @throws IOException
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     * @throws ParseException
     * @throws ObjectNotFoundException
     * @throws AlreadyExistsException
     */
    public void startIngestDataSource(String dataSourceId, boolean fullIngest) throws DocumentException, IOException, NoSuchMethodException, ClassNotFoundException, ParseException, ObjectNotFoundException, AlreadyExistsException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(dataSourceId);
        if(dataSourceContainer != null){
            DataSource dataSource = dataSourceContainer.getDataSource();
            Task harvestTask;
            dataSource.setMaxRecord4Sample(-1);
            harvestTask = new DataSourceIngestTask(String.valueOf(dataSource.getNewTaskId()), dataSource.getId(), String.valueOf(fullIngest));

            if(ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().isTaskExecuting(harvestTask)) {
                throw new AlreadyExistsException(dataSourceId);
            }
            else{
                ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().addOnetimeTask(harvestTask);
            }
        }
        else{
            throw new ObjectNotFoundException(dataSourceId);
        }
    }


    /**
     * Stop the data source ingestion
     * @param dataSourceId
     * @throws DocumentException
     * @throws IOException
     * @throws NoSuchMethodException
     * @throws ObjectNotFoundException
     * @throws ClassNotFoundException
     * @throws ParseException
     */
    public void stopIngestDataSource(String dataSourceId, Task.Status status) throws DocumentException, IOException, NoSuchMethodException, ObjectNotFoundException, ClassNotFoundException, ParseException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(dataSourceId);
        if(dataSourceContainer != null){
            List<Task> allTasks = new ArrayList<Task>();

            List<Task> runningTasks = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().getRunningTasks();
            if(runningTasks != null) {
                allTasks.addAll(runningTasks);
            }

            List<Task> onetimeTasks = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().getOnetimeTasks();
            if(onetimeTasks != null) {
                allTasks.addAll(onetimeTasks);
            }

            DataSourceTask dummyTask; // necessary to compare the action with the running tasks
            dummyTask = new DataSourceIngestTask(null, dataSourceId, null); // only works for ingestion (not implemented for exportation)

            for (Task task : allTasks) {
                if(task instanceof ScheduledTask && task.getParameters() != null && task.getParameters().length > 0) {
                    dummyTask.setTaskId(((ScheduledTask) task).getId());
                }

                if(task.equalsAction((Task) dummyTask)) {
                    task.stop(status);
                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().removeOnetimeTask(task);
                    return;
                }
            }
            throw new ObjectNotFoundException("Task not found");
        }
        else{
            throw new ObjectNotFoundException(dataSourceId);
        }
    }

    /**
     * Start the data source exportation
     * @param dataSourceId
     * @param recordsPerFile
     * @throws DocumentException
     * @throws AlreadyExistsException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws ParseException
     * @throws ObjectNotFoundException
     */
    public void startExportDataSource(String dataSourceId, String recordsPerFile, String metadataExportFormat) throws DocumentException, AlreadyExistsException, IOException, ClassNotFoundException, NoSuchMethodException, ParseException, ObjectNotFoundException {
        DataSourceContainer dataSourceContainer = getDataSourceContainer(dataSourceId);
        if(dataSourceContainer != null){
            DataSource dataSource = dataSourceContainer.getDataSource();

            FileUtils.forceMkdir(dataSource.getExportDir());

            if(metadataExportFormat == null){
                // this is a non mandatory field for REST (if it is not defined by user, it uses the default format)
                metadataExportFormat = dataSource.getMetadataFormat();
            }

            if(recordsPerFile.equals("All"))
                recordsPerFile = "-1";

            Task exportTask = new DataSourceExportTask(String.valueOf(dataSource.getNewTaskId()),dataSource.getId(),
                    dataSource.getExportDir().getAbsolutePath(), recordsPerFile, metadataExportFormat);

            if(ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().isTaskExecuting(exportTask)) {
                throw new AlreadyExistsException(dataSourceId);
            }
            ConfigSingleton.getRepoxContextUtil().getRepoxManager().getTaskManager().addOnetimeTask(exportTask);
            saveData();
        }
        else{
            throw new ObjectNotFoundException(dataSourceId);
        }
    }

    public HashMap<String, DataSourceContainer> loadDataSourceContainers() throws DocumentException, IOException {
        HashMap<String, DataSourceContainer> allDataSourceContainers = new HashMap<String, DataSourceContainer>();

        if(dataProviders != null) {
            for (DataProvider dataProvider : dataProviders) {
                allDataSourceContainers.putAll(dataProvider.getDataSourceContainers());
            }
        }
        return allDataSourceContainers;
    }



    public boolean isIdValid(String id) {
        return (id.length() <= ID_MAX_SIZE) && Pattern.compile(ID_REGULAR_EXPRESSION).matcher(id).matches();
    }


    public String getDirPathFtp(String dataSourceId){
        try {
            DataSource dataSource = null;
            while (dataSource == null){
                dataSource= getDataSource(dataSourceId);
                if(dataSource != null && ((DataSourceDirectoryImporter)dataSource).getRetrieveStrategy() instanceof DataSourceFtp){
                    return ((DataSourceDirectoryImporter) dataSource).getSourcesDirPath();
                }
            }
        } catch (DocumentException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return null;
    }

    private void setLastIngestDate(boolean useLastIngestDate, DataSource originalDataSet, DataSource targetDataSet){
        if(!useLastIngestDate && originalDataSet.getLastUpdate() != null){
            Date date = originalDataSet.getLastUpdate();
            date.setYear(70);
            targetDataSet.setLastUpdate(date);
        }else
            targetDataSet.setLastUpdate(originalDataSet.getLastUpdate());
    }
}
