package pt.utl.ist.repox.util;

import org.apache.log4j.Logger;
import org.dom4j.DocumentException;
import pt.utl.ist.repox.RepoxConfigurationDefault;
import pt.utl.ist.repox.RepoxManagerDefault;
import pt.utl.ist.repox.task.exception.IllegalFileFormatException;

import java.io.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Properties;

public class RepoxContextUtilDefault implements RepoxContextUtil {
    private static final Logger log = Logger.getLogger(RepoxContextUtilDefault.class);

    public static final String COUNTRIES_FILENAME = "countries.txt";
    public static final String DATA_PROVIDERS_FILENAME = "dataProviders.xml";
    public static final String OLD_TASKS_FILENAME = "oldTasks.xml";
    public static final String STATISTICS_FILENAME = "repoxStatistics.xml";
    public static final String RECORD_COUNTS_FILENAME = "recordCounts.xml";
    public static final String DATA_SOURCES_STATE_FILENAME = "dataSourcesStates.xml";
    private static final String SCHEDULED_TASKS_FILENAME = "scheduledTasks.xml";
    private static final String RECOVERABLE_TASKS_FILENAME = "recoverableTasks.xml";
    private static final String METADATA_TRANSFORMATIONS_FILENAME = "metadataTransformations.xml";
    private static final String EXTERNAL_SERVICES_FILENAME = "externalServices.xml";
    private static final String METADATA_SCHEMAS_FILENAME = "metadataSchemas.xml";
    private static final String TAGS_FILENAME = "dataSetTags.xml";

    private static RepoxManagerDefault repoxManager;

    public RepoxManagerDefault getRepoxManager(){
        try {
            if(repoxManager == null) {
                Properties configurationProperties = PropertyUtil.loadCorrectedConfiguration(CONFIG_FILE);/*loadCorrectedConfiguration(CONFIG_FILE);*/
                RepoxConfigurationDefault configuration = new RepoxConfigurationDefault(configurationProperties);

                // Copy countries.txt file from jar to configuration folder
                String configurationPath = configuration.getXmlConfigPath();
                File countriesFile = new File(configurationPath + File.separator + "countries.txt");
                if(!countriesFile.exists()){
                    InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("countries.txt");
                    OutputStream os = new FileOutputStream(countriesFile);
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                    os.close();
                    inputStream.close();
                }

                log.warn("Using DEFAULT configuration properties file: " + CONFIG_FILE);
                repoxManager = new RepoxManagerDefault(configuration, DATA_PROVIDERS_FILENAME, STATISTICS_FILENAME, RECORD_COUNTS_FILENAME,
                        SCHEDULED_TASKS_FILENAME, RECOVERABLE_TASKS_FILENAME, METADATA_TRANSFORMATIONS_FILENAME,
                        OLD_TASKS_FILENAME,EXTERNAL_SERVICES_FILENAME, METADATA_SCHEMAS_FILENAME,TAGS_FILENAME);
            }

            return repoxManager;
        } catch(Exception e) {
            log.fatal("Unable to load RepoxManagerDefault", e);
            return null;
        }
    }

    public RepoxManagerDefault getRepoxManagerTest() {
        Properties configurationProperties = null;
        try {
            configurationProperties = PropertyUtil.loadCorrectedConfiguration(TEST_CONFIG_FILE);//loadCorrectedConfiguration(TEST_CONFIG_FILE);
            RepoxConfigurationDefault configuration = new RepoxConfigurationDefault(configurationProperties);
            log.warn("Using TEST configuration properties file: " + TEST_CONFIG_FILE);
            repoxManager = new RepoxManagerDefault(configuration, DATA_PROVIDERS_FILENAME, STATISTICS_FILENAME, RECORD_COUNTS_FILENAME,
                    SCHEDULED_TASKS_FILENAME, RECOVERABLE_TASKS_FILENAME, METADATA_TRANSFORMATIONS_FILENAME,OLD_TASKS_FILENAME,
                    EXTERNAL_SERVICES_FILENAME, METADATA_SCHEMAS_FILENAME,TAGS_FILENAME);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (IllegalFileFormatException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return repoxManager;
    }

    /*public static Properties loadCorrectedConfiguration(String configurationFilename) throws IOException {
        URL configurationURL = Thread.currentThread().getContextClassLoader().getResource(configurationFilename);
        String configurationFile = URLDecoder.decode(configurationURL.getFile(), "ISO-8859-1");

        String fullString = "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configurationFile)));
        String currentLine = "";
        while((currentLine = reader.readLine()) != null) {
            fullString += currentLine + "\n";
        }
        reader.close();

        BufferedWriter writer = new BufferedWriter(new FileWriter(configurationFile));
        writer.write(fullString.replace('\\', '/'));
        writer.close();

        Properties configurationProperties = PropertyUtil.loadCorrectedConfiguration(configurationFilename);

        return configurationProperties;
    }*/

    public void reloadProperties(){
        try {
            Properties configurationProperties = PropertyUtil.loadCorrectedConfiguration(CONFIG_FILE);
            RepoxConfigurationDefault configuration = new RepoxConfigurationDefault(configurationProperties);
            log.warn("Using DEFAULT configuration properties file: " + CONFIG_FILE);
            repoxManager = new RepoxManagerDefault(configuration, DATA_PROVIDERS_FILENAME, STATISTICS_FILENAME, RECORD_COUNTS_FILENAME,
                    SCHEDULED_TASKS_FILENAME, RECOVERABLE_TASKS_FILENAME, METADATA_TRANSFORMATIONS_FILENAME, OLD_TASKS_FILENAME,
                    EXTERNAL_SERVICES_FILENAME, METADATA_SCHEMAS_FILENAME,TAGS_FILENAME);
        } catch(Exception e) {
            log.fatal("Unable to load RepoxManager", e);
        }
    }
}
