package pt.utl.ist.repox.services.web;

import org.dom4j.DocumentException;
import pt.utl.ist.repox.Urn;
import pt.utl.ist.repox.dataProvider.MessageType;
import pt.utl.ist.repox.metadataSchemas.MetadataSchemaVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

public interface WebServices {


    /**
     * Retrieves the Data Providers list.
     *
     * @param out
     * @throws DocumentException
     */
    public void writeDataProviders(OutputStream out) throws DocumentException, IOException;

    /**
     * Creates a new Data Provider
     * @param out
     * @param name
     * @param country
     * @param description
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void createDataProvider(OutputStream out, String name, String country, String description) throws DocumentException, IOException;

    /**
     * Updates Data Provider
     * @param out
     * @param name
     * @param country
     * @param description
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void updateDataProvider(OutputStream out, String id, String name, String country, String description) throws DocumentException, UnsupportedEncodingException, IOException;

    /**
     * Deletes Data Provider
     * @param out
     * @param id
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void deleteDataProvider(OutputStream out, String id) throws DocumentException, IOException;

    /**
     * Retrieve a Data Provider
     * @param out
     * @param dataProviderId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void getDataProvider(OutputStream out, String dataProviderId) throws DocumentException, IOException;


    /**
     * Creates a new Data Source OAI
     * @param out
     * @param dataProviderId
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param oaiSourceURL
     * @param oaiSet
     * @param marcFormat
     * @throws DocumentException
     * @throws IOException
     */
    public void createDataSourceOai(OutputStream out, String dataProviderId, String id, String description,
            String schema, String namespace, String metadataFormat, String oaiSourceURL,
            String oaiSet, String marcFormat) throws DocumentException, IOException;

    
    /**
     * @param out
     * @param dataProviderId
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param marcFormat
     * @throws DocumentException
     * @throws IOException
     */
    public void createDataSourceSruRecordUpdate(OutputStream out, String dataProviderId, String id, String description,
                                    String schema, String namespace, String metadataFormat, String marcFormat) throws DocumentException, IOException;

    /**
     * Creates a new Data Source Z3950 Timestamp
     * @param out
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
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public void createDataSourceZ3950Timestamp(OutputStream out, String dataProviderId, String id, String description,
                                               String schema, String namespace, String address, String port, String database,
                                               String user, String password, String recordSyntax, String charset, String earliestTimestampString,
                                               String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri) throws DocumentException, IOException, ParseException;

    /**
     * Creates a new Data Source Z3950 Id List
     * @param out
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
     * @param xslFile
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public void createDataSourceZ3950IdList(OutputStream out, String dataProviderId, String id, String description,
                                            String schema, String namespace, String address, String port, String database,
                                            String user, String password, String recordSyntax, String charset, InputStream xslFile,
                                            String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri) throws DocumentException, IOException, ParseException;

    /**
     * Creates a new Data Source Z3950 Id Sequence
     * @param out
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
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public void createDataSourceZ3950IdSequence(OutputStream out, String dataProviderId, String id, String description,
                                                String schema, String namespace, String address, String port, String database,
                                                String user, String password, String recordSyntax, String charset, String maximumIdString,
                                                String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri) throws DocumentException, IOException, ParseException;
    /**
     * Creates a new Data Source FTP
     * @param out
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
     * @param namespacePrefix
     * @param namespaceUri
     * @param recordXPath
     * @param server
     * @param user
     * @param password
     * @param ftpPath
     * @param marcFormat
     * @throws DocumentException
     * @throws IOException
     */
    public void createDataSourceFtp(OutputStream out, String dataProviderId, String id, String description, String schema, String namespace,
                                    String metadataFormat, String isoFormat, String charset,
                                    String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri,
                                    String recordXPath, String server, String user, String password, String ftpPath, String marcFormat) throws DocumentException, IOException;

    /**
     * Creates a new Data Source HTTP
     * @param out
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
     * @param namespacePrefix
     * @param namespaceUri
     * @param recordXPath
     * @param url
     * @param marcFormat
     * @throws DocumentException
     * @throws IOException
     */
    public void createDataSourceHttp(OutputStream out, String dataProviderId, String id, String description, String schema, String namespace,
                                    String metadataFormat, String isoFormat, String charset,
                                    String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri,
                                    String recordXPath, String url, String marcFormat) throws DocumentException, IOException;


    /**
     * Creates a new Data Source Folder
     * @param out
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
     * @param namespacePrefix
     * @param namespaceUri
     * @param recordXPath
     * @param sourcesDirPath
     * @param marcFormat
     * @throws DocumentException
     * @throws IOException
     */
    public void createDataSourceFolder(OutputStream out, String dataProviderId, String id, String description, String schema, String namespace,
                                    String metadataFormat, String isoFormat, String charset,
                                    String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri,
                                    String recordXPath, String sourcesDirPath, String marcFormat) throws DocumentException, IOException;


    /**
     * Updates a Data Source OAI
     * @param out
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param oaiSourceURL
     * @param oaiSet
     * @param marcFormat
     * @throws DocumentException
     * @throws IOException
     */
    public void updateDataSourceOai(OutputStream out, String id, String description,
            String schema, String namespace, String metadataFormat, String oaiSourceURL,
            String oaiSet, String marcFormat) throws DocumentException, IOException;

    public void updateDataSourceSruRecordUpdate(OutputStream out, String id, String description,
                                    String schema, String namespace, String metadataFormat,
                                    String marcFormat) throws DocumentException, IOException;

    /**
     * Updates a Data Source Z3950 Timestamp
     * @param out
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
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public void updateDataSourceZ3950Timestamp(OutputStream out, String id, String description,
                                               String schema, String namespace, String address, String port, String database,
                                               String user, String password, String recordSyntax, String charset, String earliestTimestampString,
                                               String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri) throws DocumentException, IOException, ParseException;

    /**
     * Updates a Data Source Z3950 Id List
     * @param out
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
     * @param xslFile
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public void updateDataSourceZ3950IdList(OutputStream out, String id, String description,
                                            String schema, String namespace, String address, String port, String database,
                                            String user, String password, String recordSyntax, String charset, InputStream xslFile,
                                            String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri) throws DocumentException, IOException, ParseException;

    /**
     * Updates a Data Source Z3950 Id Sequence
     * @param out
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
     * @throws DocumentException
     * @throws IOException
     * @throws ParseException
     */
    public void updateDataSourceZ3950IdSequence(OutputStream out, String id, String description,
                                                String schema, String namespace, String address, String port, String database,
                                                String user, String password, String recordSyntax, String charset, String maximumIdString,
                                                String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri) throws DocumentException, IOException, ParseException;
    /**
     * Updates a Data Source FTP
     * @param out
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param isoFormat
     * @param charset
     * @param recordIdPolicyClass
     * @param idXpath
     * @param namespacePrefix
     * @param namespaceUri
     * @param recordXPath
     * @param server
     * @param user
     * @param password
     * @param ftpPath
     * @param marcFormat
     * @throws DocumentException
     * @throws IOException
     */
    public void updateDataSourceFtp(OutputStream out, String id, String description, String schema, String namespace,
                                    String metadataFormat, String isoFormat, String charset,
                                    String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri,
                                    String recordXPath, String server, String user, String password, String ftpPath, String marcFormat) throws DocumentException, IOException;


    /**
     * Updates a Data Source HTTP
     * @param out
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param isoFormat
     * @param charset
     * @param recordIdPolicyClass
     * @param idXpath
     * @param namespacePrefix
     * @param namespaceUri
     * @param recordXPath
     * @param url
     * @param marcFormat
     * @throws DocumentException
     * @throws IOException
     */
    public void updateDataSourceHttp(OutputStream out, String id, String description, String schema, String namespace,
                                    String metadataFormat, String isoFormat, String charset,
                                    String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri,
                                    String recordXPath, String url, String marcFormat) throws DocumentException, IOException;


    /**
     * Updates a Data Source Folder
     * @param out
     * @param id
     * @param description
     * @param schema
     * @param namespace
     * @param metadataFormat
     * @param isoFormat
     * @param charset
     * @param recordIdPolicyClass
     * @param idXpath
     * @param namespacePrefix
     * @param namespaceUri
     * @param recordXPath
     * @param sourcesDirPath
     * @param marcFormat
     */
    public void updateDataSourceFolder(OutputStream out, String id, String description, String schema, String namespace,
                                    String metadataFormat, String isoFormat, String charset,
                                    String recordIdPolicyClass, String idXpath, String namespacePrefix, String namespaceUri,
                                    String recordXPath, String sourcesDirPath, String marcFormat) throws IOException, DocumentException;

    /**
     * Deletes Data Source
     * @param out
     * @param id
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void deleteDataSource(OutputStream out, String id) throws DocumentException, IOException;

    /**
     * Retrieve a Data Source
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void getDataSource(OutputStream out, String dataSourceId) throws DocumentException, IOException;


    /**
     * Count Records from Data Source a Data Source
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void countRecordsDataSource(OutputStream out, String dataSourceId) throws DocumentException, IOException, SQLException;


    /**
     * Returns the last ingestion date
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void lastIngestionDateDataSource(OutputStream out, String dataSourceId) throws DocumentException, IOException, SQLException;



    /**
     * Starts a Data Source ingestion
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void startIngestDataSource(OutputStream out, String dataSourceId, boolean fullIngest) throws DocumentException, IOException, NoSuchMethodException, ClassNotFoundException, ParseException;


    /**
     * Cancels a Data Source ingestion
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void stopIngestDataSource(OutputStream out, String dataSourceId) throws DocumentException, IOException, ClassNotFoundException, NoSuchMethodException, ParseException;


    /**
     * Schedules a Data Source ingestion
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void scheduleIngestDataSource(OutputStream out, String dataSourceId, String firstRunDate, String firstRunHour,
                                         String frequency, String xmonths, String fullIngest) throws DocumentException, IOException;


    /**
     * Retrieves the list of ingest schedules from a Data Source
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void scheduleListDataSource(OutputStream out, String dataSourceId) throws DocumentException, IOException;


    /**
     * Gets the status of a specific Data Source
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void harvestStatusDataSource(OutputStream out, String dataSourceId) throws DocumentException, IOException;


    /**
     * Gets the log of last Data Source ingestion
     * @param out
     * @param dataSourceId
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void logDataSource(OutputStream out, String dataSourceId) throws DocumentException, IOException;


    /**
     * Gets the active Data Source tasks
     * @param out
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void harvestingDataSources(OutputStream out) throws DocumentException, IOException;


/**
     * Starts a Data Source exportation
     * @param out
     * @param dataSourceId
     * @param recordsPerFile
     * @throws DocumentException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public void startExportDataSource(OutputStream out, String dataSourceId, String recordsPerFile, String metadataExportFormat) throws DocumentException, IOException, NoSuchMethodException, ClassNotFoundException, ParseException;



    /**
     * Retrieves the Data Source list.
     *
     * @param out
     * @throws DocumentException
     */
    public void writeDataSources(OutputStream out) throws DocumentException, IOException;

    /**
     * Retrieves the Data Source list from a specific Data Provider ID.
     *
     * @param out
     * @throws DocumentException
     */
    public void writeDataSources(OutputStream out, String dataProviderId) throws DocumentException, IOException;


    /**
     * Saves a of Record with representation recordString in DataSource with id dataSourceId and writes the result of the
     * operation to OutputStream.
     *
     * @param out
     * @param dataSourceId
     * @param recordString
     * @param recordId
     * @throws Exception
     */
    public abstract void saveRecord(OutputStream out, String recordId, String dataSourceId, String recordString) throws Exception;

    /**
     * Marks Record with urn recordId
     * of the operation to OutputStream.
     *
     * @param out
     * @param recordId
     * @throws Exception
     */
    public abstract void deleteRecord(OutputStream out, String recordId) throws Exception;

    /**
     * Permanently removes the Record with urn recordId
     * of the operation to OutputStream.
     *
     * @param out
     * @param recordId
     * @throws Exception
     */
    public abstract void eraseRecord(OutputStream out, String recordId) throws Exception;

    /**
     * Retrieves Record with urn and writes it to OutputStream.
     *
     * @param out
     * @param recordUrn
     * @throws Exception
     */
    public abstract void getRecord(OutputStream out, Urn recordUrn) throws Exception;

    public void createMetadataSchema(OutputStream out, String id, String oldSchemaId, String namespace,String designation,String description,
                                     String notes,Boolean oaiAvailable,List<MetadataSchemaVersion> metadataSchemaVersions);
    public void updateMetadataSchema(OutputStream out, String id, String oldSchemaId,String namespace,String designation,String description,
                                     String notes,Boolean oaiAvailable,List<MetadataSchemaVersion> metadataSchemaVersions);
    public void removeMetadataSchema(OutputStream out, String id);
    public void listMetadataSchemas(OutputStream out);

    public void createMapping(OutputStream out, String id,String description,String srcSchemaId,String srcSchemaVersion,
                              String destSchemaId,String destSchemaVersion,String isXslVersion2,
                              String xslFilename,InputStream xsdFile);
    public void updateMapping(OutputStream out, String id,String description,String srcSchemaId,String srcSchemaVersion,
                              String destSchemaId,String destSchemaVersion,String isXslVersion2,
                              String xslFilename,InputStream xsdFile, String oldId);
    public void removeMapping(OutputStream out, String id);
    public void listMappings(OutputStream out);

    /**
     * Retrieves Statistics for the REPOX Instance.
     *
     * @param out
     * @param type
     * @throws Exception
     */
    public void getStatistics(OutputStream out, String type);

    public void createErrorMessage(OutputStream out, MessageType type, String cause);
}