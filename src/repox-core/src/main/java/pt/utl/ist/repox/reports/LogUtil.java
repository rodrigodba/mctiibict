package pt.utl.ist.repox.reports;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.DefaultElement;
import pt.utl.ist.repox.util.TimeUtil;
import pt.utl.ist.repox.util.XmlUtil;
import pt.utl.ist.util.DateUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created to REPOX Project.
 * User: Edmundo
 * Date: 26-01-2012
 * Time: 18:53
 */
public class LogUtil {
    private static Logger log = Logger.getLogger(LogUtil.class);

    public static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private static Map<File,Document> logFiles = new HashMap<File, Document>();

    public static String formatIntoHHMMSS(long secsIn) {
        long hours = secsIn / 3600;
        long remainder = secsIn % 3600;
        long minutes = remainder / 60;
        long seconds = remainder % 60;

        return ( (hours < 10 ? "0" : "") + hours
                + ":" + (minutes < 10 ? "0" : "") + minutes
                + ":" + (seconds< 10 ? "0" : "") + seconds );
    }

    public static String dateDiff(Date date1, Date date2) {
        long milliseconds1 = date1.getTime();
        long milliseconds2 = date2.getTime();
        long diff = milliseconds2 - milliseconds1;
        long diffSeconds = diff / 1000;
        long diffMinutes = diff / (60 * 1000);
        long diffHours = diff / (60 * 60 * 1000);
        long diffDays = diff / (24 * 60 * 60 * 1000);

        diffSeconds = diffSeconds - (diffMinutes * 60);
        diffMinutes = diffMinutes - (diffHours * 60);
        diffHours = diffHours - (diffDays * 24);

        return ( (diffHours < 10 ? "0" : "") + diffHours
                + ":" + (diffMinutes < 10 ? "0" : "") + diffMinutes
                + ":" + (diffSeconds< 10 ? "0" : "") + diffSeconds );
    }

    // LOG LIb To BE

    public static Document getLogDocument(File logFile){
        Document logDocument = null;
        try {
            SAXReader reader = new SAXReader();
            logDocument = reader.read(logFile);
//                System.out.println("---U---UPDATING REPORT FILE");
//            logger.warn("---U---UPDATING REPORT FILE");
        } catch (DocumentException e) {
            logDocument = createNewLogDocument();
//            logger.warn("---C---CREATING NEW REPORT FILE");
        }
        return logDocument;
    }

    public static void updateLogFile(File logFile,Document logDocument, List<LogElement> logElements){
        List content = logDocument.getRootElement().content();

        // new file verification
        if(content.size() > 0){
            updateAllNodes(logDocument,logElements);
        }else{
            for(LogElement logElement : logElements){
                switch (logElement.getType()){
                    case ERROR:
                        createErrorLogEntry(logDocument,(ErrorLogElement)logElement);
                        break;
                    case WARNING:
                        createWarningLogEntry(logDocument,((WarningLogElement)logElement));
                        break;
                    case INFO:
                        createInfoLogEntry(logDocument, ((InfoLogElement) logElement));
                        break;
                    default:
                        addXMLElement(logElement.getId(),logElement.getValue(),content);
                        break;
                }
            }
        }

        writeLogFile(logFile,logDocument);
    }

    public static void removeLogFile(File logFile){
        logFiles.remove(logFile);
    }

    public static Document getCorrectLogDocument(File logFile){
        Document document = logFiles.get(logFile);
        if(document == null){
            document = getLogDocument(logFile);
            logFiles.put(logFile,document);
        }
        return document;
    }

    public static void addSimpleInfoLog(String message, Class logClass, File logFile, boolean writeXmlFile){
        Document logDocument = getCorrectLogDocument(logFile);
        InfoLogElement infoLogElement = new InfoLogElement("info",message,new Date(),logClass.getName());
        createInfoLogEntry(logDocument, infoLogElement);
        if(writeXmlFile)
            writeLogFile(logFile,logDocument);
    }

    public static void addSimpleErrorLog(String message, Class logClass, File logFile, Exception e){
        Document logDocument = getCorrectLogDocument(logFile);
        ErrorLogElement errorLogElement = new ErrorLogElement("error",message,new Date(),logClass.getName(),e);
        createErrorLogEntry(logDocument, errorLogElement);
        writeLogFile(logFile,logDocument);
    }

    public static void addEmptyRecordCount(String recordId, File logFile){
        Document logDocument = getCorrectLogDocument(logFile);

        Element emptyRecordsEl;
        if(logDocument.getRootElement().selectSingleNode("emptyMetadata") == null){
            emptyRecordsEl = logDocument.getRootElement().addElement("emptyMetadata");
            emptyRecordsEl.addAttribute("description","Number of records with an empty metadata element");
        } else{
            Element element = (Element)logDocument.getRootElement().selectSingleNode("emptyMetadata");
            int currentIndex = logDocument.getRootElement().content().indexOf(element);
            int lastDocPosition = logDocument.getRootElement().content().size()-2;
            if(currentIndex != lastDocPosition){
                element.detach();
                logDocument.getRootElement().content().add(lastDocPosition,element);
            }

            emptyRecordsEl = element;
        }

        Element singleErrorNode = emptyRecordsEl.addElement("record");
        singleErrorNode.addAttribute("id", recordId);

        if(emptyRecordsEl.attributeValue("total") == null)
            emptyRecordsEl.addAttribute("total","1");
        else
            emptyRecordsEl.addAttribute("total",String.valueOf(Integer.valueOf(emptyRecordsEl.valueOf("@total")) + 1));

        writeLogFile(logFile,logDocument);
    }

    public static void addDuplicateRecordCount(String recordId, File logFile){
        if(logFile == null)
            return;

        Document logDocument = getCorrectLogDocument(logFile);

        Element emptyRecordsEl;
        if(logDocument.getRootElement().selectSingleNode("duplicatedRecords") == null){
            emptyRecordsEl = logDocument.getRootElement().addElement("duplicatedRecords");
            emptyRecordsEl.addAttribute("description","Number of duplicated records");
        } else{
            Element element = (Element)logDocument.getRootElement().selectSingleNode("duplicatedRecords");
            int currentIndex = logDocument.getRootElement().content().indexOf(element);
            int lastDocPosition = logDocument.getRootElement().content().size()-2;
            if(currentIndex != lastDocPosition){
                element.detach();
                logDocument.getRootElement().content().add(lastDocPosition,element);
            }

            emptyRecordsEl = element;
        }

        Element singleErrorNode = emptyRecordsEl.addElement("record");
        singleErrorNode.addAttribute("id", recordId);

        if(emptyRecordsEl.attributeValue("total") == null)
            emptyRecordsEl.addAttribute("total","1");
        else
            emptyRecordsEl.addAttribute("total",String.valueOf(Integer.valueOf(emptyRecordsEl.valueOf("@total")) + 1));

        writeLogFile(logFile,logDocument);
    }

    public static void startLogInfo(File logFile, Date startTime, String status, String set){
        List<LogElement> logElements = new ArrayList<LogElement>();
        logElements.add(new LogElement("status",status));
        logElements.add(new LogElement("dataSetId",set));
        logElements.add(new LogElement("startTime",LogUtil.convertDateToString(startTime)));
        logElements.add(new LogElement("endTime","--"));
        logElements.add(new LogElement("duration","--"));
        logElements.add(new LogElement("records",String.valueOf(0)));
        logElements.add(new LogElement("deleted",String.valueOf(0)));
        Document logDocument = getCorrectLogDocument(logFile);
        LogUtil.updateLogFile(logFile,logDocument,logElements);
    }

    public static void endLogInfo(File logFile, Date startTime, Date endTime, String status, String set, int records,
                                  int deleted){
        List<LogElement> logElements = new ArrayList<LogElement>();
        logElements.add(new LogElement("status",status));
        logElements.add(new LogElement("dataSetId",set));
        logElements.add(new LogElement("startTime",LogUtil.convertDateToString(startTime)));
        logElements.add(new LogElement("endTime",LogUtil.convertDateToString(endTime)));
        logElements.add(new LogElement("duration",LogUtil.dateDiff(startTime, endTime)));
        logElements.add(new LogElement("records",String.valueOf(records)));
        logElements.add(new LogElement("deleted",String.valueOf(deleted)));
        Document logDocument = getCorrectLogDocument(logFile);
        LogUtil.updateLogFile(logFile,logDocument,logElements);
    }

    private static void createErrorLogEntry(Document logDocument, ErrorLogElement logElement){
//        Element errorsNode;
//        if(logDocument.getRootElement().selectSingleNode("errors") == null)
//            errorsNode = logDocument.getRootElement().addElement("errors");
//        else
//            errorsNode = (Element)logDocument.getRootElement().selectSingleNode("errors");
//
//        Element singleErrorNode = errorsNode.addElement(logElement.getId());
//        singleErrorNode.addAttribute("failedRecordId",logElement.getFailedId());
//        singleErrorNode.addElement("errorCause").setText(logElement.getErrorCause());

        Element warningsNode;
        if(logDocument.getRootElement().selectSingleNode("errors") == null){
            warningsNode = logDocument.getRootElement().addElement("errors");
        } else{
            Element element = (Element)logDocument.getRootElement().selectSingleNode("errors");
            int currentIndex = logDocument.getRootElement().content().indexOf(element);
            int lastDocPosition = logDocument.getRootElement().content().size()-2;
            if(currentIndex != lastDocPosition){
                element.detach();
                logDocument.getRootElement().content().add(lastDocPosition,element);
            }

            warningsNode = element;
        }

        Element singleErrorNode = warningsNode.addElement(logElement.getId());
        singleErrorNode.addAttribute("time", DateUtil.date2String(logElement.getOccurenceTime(), TimeUtil.LONG_DATE_FORMAT_TIMEZONE));
        singleErrorNode.addAttribute("class",logElement.getOccurenceClass());

        StringWriter sw = new StringWriter();
        logElement.getInputException().printStackTrace(new PrintWriter(sw));
//        String stacktrace = ;
        singleErrorNode.setText(sw.toString());
    }

    private static void createWarningLogEntry(Document logDocument, WarningLogElement logElement){
        Element warningsNode;
        if(logDocument.getRootElement().selectSingleNode("warnings") == null){
            warningsNode = logDocument.getRootElement().addElement("warnings");
            warningsNode.addAttribute("resultFile", "" + logElement.getResultLink() + "");
        } else
            warningsNode = (Element)logDocument.getRootElement().selectSingleNode("warnings");

        Element singleErrorNode = warningsNode.addElement("warning");
        singleErrorNode.addAttribute("warningRecordId",logElement.getFailedId());
//        singleErrorNode.addAttribute("warningCause","Schematron Validation warnings");
    }

    private static void createInfoLogEntry(Document logDocument, InfoLogElement logElement){
        Element warningsNode;
        if(logDocument.getRootElement().selectSingleNode("summary") == null){
            warningsNode = logDocument.getRootElement().addElement("summary");
        } else{
            Element element = (Element)logDocument.getRootElement().selectSingleNode("summary");
            int currentIndex = logDocument.getRootElement().content().indexOf(element);
            int lastDocPosition = logDocument.getRootElement().content().size()-1;
            if(currentIndex != lastDocPosition){
                element.detach();
                logDocument.getRootElement().content().add(lastDocPosition,element);
            }

            warningsNode = element;
        }

        Element singleErrorNode = warningsNode.addElement(logElement.getId());
        singleErrorNode.addAttribute("time", DateUtil.date2String(logElement.getOccurenceTime(), TimeUtil.LONG_DATE_FORMAT_TIMEZONE));
        singleErrorNode.addAttribute("class",logElement.getOccurenceClass());
        singleErrorNode.setText(logElement.getValue());
    }

    private static void updateAllNodes(Document logDocument, List<LogElement> logElements){
        for(LogElement logElement : logElements){
            switch (logElement.getType()){
                case ERROR:
                    createErrorLogEntry(logDocument,(ErrorLogElement)logElement);
                    break;
                case WARNING:
                    createWarningLogEntry(logDocument,((WarningLogElement)logElement));
                    break;
                case INFO:
                    createInfoLogEntry(logDocument, ((InfoLogElement) logElement));
                    break;
                default:
                    updateNode(logElement.getId(), logElement.getValue(), logDocument);
                    break;
            }
        }
    }

    private static void updateNode(String nodeID, String value, Document logDocument){
        Element node;
        if(logDocument.getRootElement().selectSingleNode(nodeID) == null)
            node = logDocument.getRootElement().addElement(nodeID);
        else
            node = (Element)logDocument.getRootElement().selectSingleNode(nodeID);

        node.setText(value);
    }

    public static Date convertStringToDate(String date){
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        String dateString = date.toString();
        try {
            Date parsed = format.parse(dateString);
            return parsed;
        }
        catch(ParseException pe) {
            log.error("ERROR: Cannot parse " + dateString );
            return null;
        }
    }

    public static String convertDateToString(Date date){
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        return sdf.format(date);
    }

    private static void addXMLElement(String elemId, String value, List content){
        DefaultElement startTime = new DefaultElement(elemId);
        startTime.setText(value);
        content.add(0, startTime);
    }

    private static Document createNewLogDocument(){
        Document document = DocumentHelper.createDocument();
        document.addElement("report");
        return document;
    }

    private static void writeLogFile(File logFile, Document logDocument){
        try {
            XmlUtil.writePrettyPrint(logFile, logDocument);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args){
        Date date1 = new Date("Wed Feb 01 11:16:46 GMT 2012");
        Date date2 = new Date("Wed Feb 01 11:20:28 GMT 2012");

        System.out.println(dateDiff(date1, date2));
    }
}
