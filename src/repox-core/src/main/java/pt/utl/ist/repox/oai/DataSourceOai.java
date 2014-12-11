package pt.utl.ist.repox.oai;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.oclc.oai.harvester2.verb.ListIdentifiers;
import org.w3c.dom.NodeList;
import pt.utl.ist.repox.dataProvider.DataProvider;
import pt.utl.ist.repox.dataProvider.DataSource;
import pt.utl.ist.repox.dataProvider.dataSource.FileRetrieveStrategy;
import pt.utl.ist.repox.dataProvider.dataSource.RecordIdPolicy;
import pt.utl.ist.repox.metadataTransformation.MetadataFormat;
import pt.utl.ist.repox.metadataTransformation.MetadataTransformation;
import pt.utl.ist.repox.recordPackage.RecordRepox;
import pt.utl.ist.repox.reports.LogUtil;
import pt.utl.ist.repox.statistics.RecordCount;
import pt.utl.ist.repox.task.Task;
import pt.utl.ist.repox.util.CompareUtil;
import pt.utl.ist.repox.util.ConfigSingleton;
import pt.utl.ist.repox.util.StringUtil;
import pt.utl.ist.repox.util.TimeUtil;
import pt.utl.ist.util.DateUtil;
import javax.xml.transform.TransformerConfigurationException;
import java.io.*;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.*;

/**
 * Implementation of a DataSource that makes requests to a OAI-PMH Provider and
 * harvests its Records into the DataSource
 */
public class DataSourceOai extends DataSource {
    private static final Logger log = Logger.getLogger(DataSourceOai.class);

    private String oaiSourceURL;
    private String oaiSet;

    private FileRetrieveStrategy retrieveStrategy;

    public FileRetrieveStrategy getRetrieveStrategy() {
        return retrieveStrategy;
    }

    public void setRetrieveStrategy(FileRetrieveStrategy retrieveStrategy) {
        this.retrieveStrategy = retrieveStrategy;
    }

    public String getOaiSourceURL() {
        return oaiSourceURL;
    }

    public void setOaiSourceURL(String oaiSourceURL) {
        this.oaiSourceURL = oaiSourceURL;
    }

    public String getOaiSet() {
        return oaiSet;
    }

    public void setOaiSet(String oaiSet) {
        this.oaiSet = oaiSet;
    }

    public static List<String> getOaiMetadataFormats() {
        List<String> oaiMetadataFormats = new ArrayList<String>();
        oaiMetadataFormats.add(MetadataFormat.oai_dc.toString());
        oaiMetadataFormats.add(MetadataFormat.ese.toString());
        oaiMetadataFormats.add(MetadataFormat.tel.toString());
        oaiMetadataFormats.add(MetadataFormat.MarcXchange.toString());

        return oaiMetadataFormats;
    }

    public DataSourceOai() {
        super();
        metadataFormat = MetadataFormat.oai_dc.toString();
    }


    public DataSourceOai(DataProvider dataProvider, String id, String description, String schema, String namespace, String metadataFormat,
                         RecordIdPolicy recordIdPolicy, Map<String, MetadataTransformation> metadataTransformations) {
        super(dataProvider, id, description, schema, namespace, metadataFormat, recordIdPolicy, metadataTransformations);
    }

    public DataSourceOai(DataProvider dataProvider, String id, String description, String schema, String namespace, String metadataFormat,
                         String oaiSourceURL, String oaiSet, RecordIdPolicy recordIdPolicy,
                         Map<String, MetadataTransformation> metadataTransformations) {
        this(dataProvider, id, description, schema, namespace, metadataFormat, recordIdPolicy, metadataTransformations);
        this.oaiSourceURL = oaiSourceURL;
        this.oaiSet = oaiSet;
    }

    @Override
    public Task.Status ingestRecords(File logFile, boolean fullIngest) throws TransformerConfigurationException, IOException, DocumentException, SQLException {
        log.debug("INGESTING NOW - OAI!");

        Task.Status ingestStatus = Task.Status.OK;

        Harvester harvester = new Harvester(oaiSourceURL, oaiSet, null, null, metadataFormat, logFile, maxRecord4Sample);
        String outputDirPath = Harvester.getOutputDirPath(oaiSourceURL, oaiSet);
        File ingestResumptionFile = new File(outputDirPath, "ingestResumption.txt");

        Date startIngestTime = new Date();
        LogUtil.startLogInfo(logFile, startIngestTime, ingestStatus.name(), id);

        if(fullIngest) {
            boolean successfulDeletion = emptyRecords();
            if(!successfulDeletion) {
                StringUtil.simpleLog("Importing aborted - unable to delete the current Records", this.getClass(), logFile);
                LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.ERROR.name(),id,lastIngestCount,lastIngestDeletedCount);
                return Task.Status.FAILED;
            }

            if(ingestResumptionFile.exists()) {
                ingestResumptionFile.delete();
            }

            //Clear the last ingest date
            setLastUpdate(null);

            //Update the XML file
            ConfigSingleton.getRepoxContextUtil().getRepoxManager().getDataManager().saveData();
        }

        File requestOutputDir = new File(outputDirPath);
        if(!requestOutputDir.exists() && !requestOutputDir.mkdir()) {
            throw new RuntimeException("Unable to create directory: " + outputDirPath);
        }

        RecordCount recordCount = ConfigSingleton.getRepoxContextUtil().getRepoxManager().getRecordCountManager().getRecordCount(id, true);

        //if there is a previous successful harvest and has finished
        if(recordCount != null && recordCount.getCount() > 0 && lastUpdate != null && !ingestResumptionFile.exists()) {
            String syncDateString = DateUtil.date2String(lastUpdate, TimeUtil.SHORT_DATE_FORMAT);
            harvester.setFromDateString(syncDateString);
            StringUtil.simpleLog("OAI-PMH harvest from date: " + syncDateString, this.getClass(), logFile);
        }

        // Harvest the records
        Thread harvesterThread = new Thread(harvester);
        harvesterThread.start();
        int currentRequest = 1;
        try {
            if(ingestResumptionFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(ingestResumptionFile));
                currentRequest = Integer.parseInt(reader.readLine());
                reader.close();
                StringUtil.simpleLog("Continuing from previous stopped ingestion - request " + currentRequest, this.getClass(), logFile);
            }

            File currentRequestFile = harvester.getRequestFile(currentRequest);
            TimeUtil.startTimers();
            TimeUtil.getTimeSinceLastTimerArray(8);

            while(true) {
                if(stopExecution) {
                    harvester.stop();
                    if(forceStopExecution){
                        LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.ERROR.name(),id,lastIngestCount, lastIngestDeletedCount);
                        return Task.Status.FORCE_EMPTY;
                    }
                    StringUtil.simpleLog("Received stop signal: exiting import.", this.getClass(), logFile);
                    LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.CANCELED.name(),id,lastIngestCount, lastIngestDeletedCount);
                    return Task.Status.CANCELED;
                }
                while(!currentRequestFile.exists()) { // Wait for the Harvester to write the request file
                    if(stopExecution) {
                        harvester.stop();
                        if(forceStopExecution){
                            LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.ERROR.name(),id,lastIngestCount, lastIngestDeletedCount);
                            return Task.Status.FORCE_EMPTY;
                        }
                        StringUtil.simpleLog("Received stop signal: exiting import.", this.getClass(), logFile);
                        LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.CANCELED.name(),id,lastIngestCount, lastIngestDeletedCount);
                        return Task.Status.CANCELED;
                    }
                    if(harvester.isHarvestFinished()) {
                        StringUtil.simpleLog("Ingest Process ended. Exiting.", this.getClass(), logFile);
                        log.info("* Total Time: " + TimeUtil.getTimeSinceLastTimerArray(8));

                        harvester.cleanUp();
                        LogUtil.endLogInfo(logFile, startIngestTime, new Date(), ingestStatus.name(),id,lastIngestCount, lastIngestDeletedCount);
                        return ingestStatus;
                    }
                    if(harvester.getRequestFileNoRecords().exists()){
                        //StringUtil.simpleLog("Harvester result: " + harvester.readErrorCode(), this.getClass(), logFile);
                        LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.OK.name(),id,lastIngestCount, lastIngestDeletedCount);
                        return Task.Status.OK;
                    }

                    if(!harvesterThread.isAlive() && !harvester.isHarvestFinished() && getMaxRecord4Sample() == -1) {
                        StringUtil.simpleLog("Harvester thread exited without finishing. Exiting ingesting Data Source Oai.", this.getClass(), logFile);
                        LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.ERROR.name(),id,lastIngestCount, lastIngestDeletedCount);
                        return Task.Status.FAILED;
                    }
                    Thread.sleep(1000);
                }
                Thread.sleep(500); // avoid trying to read request being written


                statisticsHarvester = harvester.getStatisticsHarvest();

                if(currentRequest == 1){
                    ArrayList<Integer> infos = harvester.getServerInfos();
                    if(infos != null && infos.size() > 1){
                        numberOfRecords2Harvest = infos.get(0);
                        numberOfRecordsPerResponse = infos.get(1);
                    }
                }

                // Split the OAI-PMH answer to Records and import them
                Map<String, RecordRepox> batchRecordsWithoutDuplicates = new HashMap<String, RecordRepox>();
                TimeUtil.getTimeSinceLastTimerArray(9);
                ResponseTransformer responseTransformer = new ResponseTransformer();
                List<RecordRepox> responseRecords;
                try {
                    responseRecords = responseTransformer.splitResponseToRecords(currentRequestFile, this, logFile);
                }
                catch(DocumentException e) { // may be trying to read a request being written, wait for file to be written and retry
                    log.info("Error reading XML (waiting 5s and retrying in case it's being written): " + e.getMessage(), e);
                    Thread.sleep(5000);
                    responseRecords = responseTransformer.splitResponseToRecords(currentRequestFile, this, logFile);
                }

                // to avoid records with duplicated ID's
                for (RecordRepox responseRecord : responseRecords) {
                    batchRecordsWithoutDuplicates.put(responseRecord.getId().toString(), responseRecord);
                }

                List<RecordRepox> batchRecords = new ArrayList<RecordRepox>(batchRecordsWithoutDuplicates.values());
                lastIngestCount += batchRecords.size();

                log.info("Time for splitting " + responseRecords.size() + " records from response file: " + TimeUtil.getTimeSinceLastTimerArray(9));

                while(batchRecords.size() > RECORDS_BATCH_SIZE) {
                    List<RecordRepox> recordsToImport = batchRecords.subList(0, RECORDS_BATCH_SIZE);
                    batchRecords = batchRecords.subList(RECORDS_BATCH_SIZE, batchRecords.size());

                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().processRecords(this, recordsToImport,logFile);

                    log.info("Time for importing " + recordsToImport.size() + " records to DB: " + TimeUtil.getTimeSinceLastTimerArray(9));
                }
                if(!batchRecords.isEmpty()) {
                    ConfigSingleton.getRepoxContextUtil().getRepoxManager().getAccessPointsManager().processRecords(this, batchRecords,logFile);
                    log.info("Time for importing last " + batchRecords.size() + " records to DB: " + TimeUtil.getTimeSinceLastTimerArray(9));
                }

                addDeletedRecords(batchRecords);

                currentRequestFile.delete();
                currentRequest++;
                OutputStream resumptionOutputStream = new FileOutputStream(ingestResumptionFile);
                resumptionOutputStream.write((currentRequest + "\n").getBytes("UTF-8"));
                resumptionOutputStream.close();

                currentRequestFile = harvester.getRequestFile(currentRequest);
            }
        }
        catch(Exception e) {
            if(stopExecution) {
                harvester.stop();
                if(forceStopExecution){
                    LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.ERROR.name(),id,lastIngestCount, lastIngestDeletedCount);
                    return Task.Status.FORCE_EMPTY;
                }
                StringUtil.simpleLog("Received stop signal: exiting import.", this.getClass(), logFile);
                LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.CANCELED.name(),id,lastIngestCount, lastIngestDeletedCount);
                return Task.Status.CANCELED;
            }
            log.error("Error ingesting : " + e.getMessage(), e);
            harvester.stop();
            StringUtil.simpleLog("Error ingesting. Exiting ingesting Data Source Oai.", e, this.getClass(), logFile);
            LogUtil.endLogInfo(logFile, startIngestTime, new Date(), StatusDS.ERROR.name(),id,lastIngestCount, lastIngestDeletedCount);
            return Task.Status.ERRORS;
        }
    }

    @Override
    public boolean isWorking() {
        try {
            ListIdentifiers listIdentifiers = new ListIdentifiers(oaiSourceURL, null, null, oaiSet, metadataFormat);
            if(listIdentifiers.isResultEmpty()) {
                return false;
            }
            NodeList errors = listIdentifiers.getErrors();
            if(errors.getLength() > 0) {
                return false;
            }
        }
        catch(FileNotFoundException e) { //This is the error returned by a 404
            return false;
        }
        catch(Exception e) {
            return false;
        }

        return true;
    }

    public boolean isSameDataSource(DataSourceOai dSOai) {
        return CompareUtil.compareObjectsAndNull(this.oaiSourceURL, dSOai.getOaiSourceURL())
                && CompareUtil.compareObjectsAndNull(this.oaiSet, dSOai.getOaiSet());
    }

    @Override
    public boolean equals(Object obj) {
        if(!this.getClass().equals(obj.getClass())) {
            return false;
        }

        DataSourceOai dSOai = (DataSourceOai) obj;
        return equalsBaseProperties(dSOai) && isSameDataSource(dSOai);

    }

    @Override
    public void cleanUp() throws IOException, DocumentException {
        super.cleanUp();

        String outputDirPath = Harvester.getOutputDirPath(oaiSourceURL, oaiSet);
        File outputDir = new File(outputDirPath);

        if(outputDir.exists()) {
            try{
                FileUtils.deleteDirectory(outputDir);
                log.info("Deleted OAI-PMH dir with success from Data Source with id " + id);
                outputDir.mkdir();
            }
            catch (IOException e){
                log.error("Unable to delete OAI-PMH dir from Data Source with id " + id);
            }
        }
    }

    @Override
    public Element addSpecificInfo(Element sourceElement) {
        sourceElement.addAttribute("type", "DataSourceOai");
        sourceElement.addElement("oai-source").setText(getOaiSourceURL());
        if(getOaiSet() != null && !getOaiSet().isEmpty()) {
            sourceElement.addElement("oai-set").setText(getOaiSet());
        }
        return sourceElement;
    }

    @Override
    public int getTotalRecords2Harvest() {
        return numberOfRecords2Harvest;
    }

    @Override
    public String getNumberOfRecords2HarvestStr() {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.GERMAN);
        return numberFormat.format(numberOfRecords2Harvest);
//        return String.valueOf(numberOfRecords2Harvest);
    }

    @Override
    public int getRecordsPerResponse() {
        return numberOfRecordsPerResponse;
    }

    @Override
    public ArrayList<Long> getStatisticsHarvester() {
        return statisticsHarvester;  //To change body of implemented methods use File | Settings | File Templates.
    }

}
