/*
 * Leader.java
 *
 * Created on 20 de Julho de 2002, 19:31
 */

package pt.utl.ist.marc.util;

import java.text.DecimalFormat;

/**
 * <p><code>Leader</code> defines behaviour for the record label
 * (record position 00-23).  </p>
 *
 * <p>The leader is a fixed field that occurs at the beginning of a
 * MARC record and provides information for the processing of the record.
 * The structure of the leader according to the MARC standard is as
 * follows:</p>
 * <pre>
 * RECORD_LENGTH RECORD_STATUS TYPE_OF_RECORD IMPLEMENTATION-DEFINED
 * 00-04         05            06             07-08
 *   CHARACTER_CODING_SCHEME  INDICATOR_COUNT SUBFIELD_CODE_LENGTH
 *   09                       10              11
 *     BASE_ADDRESS_OF_DATA  IMPLEMENTATION-DEFINED  ENTRY_MAP
 *     12-16                 17-19                   20-23
 * </pre>
 * <p>This structure is returned by the {@link #getSerializedForm()}
 * method.</p>
 *
 * @author Bas Peters - <a href="mailto:mail@bpeters.com">mail@bpeters.com</a>
 * @version 0.2
 */
public class Leader {

    /** The logical record length. */
    protected int recordLength;
    /** The record status. */
    protected char recordStatus;
    /** Type of record. */
    protected char typeOfRecord;
    /** Implementation defined. */
    protected char[] implDefined1;
    /** Character coding scheme. */
    protected char charCodingScheme;
    /** The indicator count. */
    protected char indicatorCount;
    /** The subfield code length. */
    protected char subfieldCodeLength;
    /** The base address of data. */
    protected int baseAddressOfData;
    /** Implementation defined. */
    protected char[] implDefined2;
    /** Entry map. */
    protected char[] entryMap;

    /** number format for both record length and base address of data */
    DecimalFormat df = new DecimalFormat("00000");

    public Leader(){
    }

    public Leader(String leaderStr){
        while (leaderStr.length()<25)
            leaderStr+=" ";

        int recordLength=0;
        int baseAddress=0;
        try {
	        recordLength=Integer.parseInt(leaderStr.substring(0, 5));
	        baseAddress=Integer.parseInt(leaderStr.substring(12, 17));
        }catch(NumberFormatException e) {        	
        }
        setRecordLength(recordLength);
        setRecordStatus(leaderStr.charAt(5));
        setTypeOfRecord(leaderStr.charAt(6));
        setImplDefined1(leaderStr.substring(7,9).toCharArray());
        setCharCodingScheme(leaderStr.charAt(9));
        setIndicatorCount(leaderStr.charAt(10));
        setSubfieldCodeLength(leaderStr.charAt(11));
        setBaseAddressOfData(baseAddress);
        setImplDefined2(leaderStr.substring(17, 20).toCharArray());
        setEntryMap(leaderStr.substring(20, 24).toCharArray());    
    }
    
    
    
     /**
     * <p>Registers the logical record length (positions 00-04).  </p>
     *
     * @param recordLength integer representing the
     *                     record length
     */
    public void setRecordLength(int recordLength) {
	this.recordLength = recordLength;
    }

    /**
     * <p>Registers the record status (position 05).</p>
     *
     * @param recordStatus character representing the
     *                     record status
     */
    public void setRecordStatus(char recordStatus) {
	this.recordStatus = recordStatus;
    }

    /**
     * <p>Registers the type of record (position 06).</p>
     *
     * @param recordLength character representing the
     *                     type of record
     */
    public void setTypeOfRecord(char typeOfRecord) {
	this.typeOfRecord = typeOfRecord;
    }

    /**
     * <p>Registers implementation defined values (position 07-08).</p>
     *
     * @param implDefined1 character array representing the
     *                     implementation defined data
     */
    public void setImplDefined1(char[] implDefined1) {
	this.implDefined1 = implDefined1;
    }

    /**
     * <p>Registers the character encoding scheme
     * (position 09).</p>
     *
     * @param charCodingScheme character representing the
     *                         character encoding
     */
    public void setCharCodingScheme(char charCodingScheme) {
	this.charCodingScheme = charCodingScheme;
    }

    /**
     * <p>Registers the indicator count (position 10).</p>
     *
     * @param indicatorCount character representing the
     *                       number of indicators present
     *                       in a data field
     */
    public void setIndicatorCount(char indicatorCount) {
	this.indicatorCount = indicatorCount;
    }

    /**
     * <p>Registers the subfield code length (position 11).</p>
     *
     * @param subfieldCodeLength character representing the
     *                           subfield code length
     */
    public void setSubfieldCodeLength(char subfieldCodeLength) {
	this.subfieldCodeLength = subfieldCodeLength;
    }

    /**
     * <p>Registers the base address of data (positions 12-16).</p>
     *
     * @param baseAddressOfData integer representing the
     *                          base address of data
     */
    public void setBaseAddressOfData(int baseAddressOfData) {
	this.baseAddressOfData = baseAddressOfData;
    }

    /**
     * <p>Registers implementation defined values (positions 17-19).</p>
     *
     * @param implDefined2 character array representing the
     *                     implementation defined data
     */
    public void setImplDefined2(char[] implDefined2) {
	this.implDefined2 = implDefined2;
    }

    /**
     * <p>Registers the entry map (positions 20-23).</p>
     *
     * @param entryMap character array representing the
     *                 entry map
     */
    public void setEntryMap(char[] entryMap) {
	this.entryMap = entryMap;
    }

    /**
     * <p>Returns the logical record length (positions 00-04).</p>
     *
     * @return <code>int</code> - the record length
     */
    public int getRecordLength() {
	return recordLength;
    }

    /**
     * <p>Returns the record status (positions 05).</p>
     *
     * @return <code>char</code> - the record status
     */
    public char getRecordStatus() {
	return recordStatus;
    }

    /**
     * <p>Returns the record type (position 06).</p>
     *
     * @return <code>char</code> - the record type
     */
    public char getTypeOfRecord() {
	return typeOfRecord;
    }

    /**
     * <p>Returns implementation defined values
     * (positions 07-08).</p>
     *
     * @return <code>char[]</code> - implementation defined values
     */
    public char[] getImplDefined1() {
	return implDefined1;
    }

    /**
     * <p>Returns the character coding scheme (position 09).</p>
     *
     * @return <code>char</code> - the character coding scheme
     */
    public char getCharCodingScheme() {
	return charCodingScheme;
    }

    /**
     * <p>Returns the indicator count (positions 10).</p>
     *
     * @return <code>char</code> - the indicator count
     */
    public char getIndicatorCount() {
	return indicatorCount;
    }

    /**
     * <p>Returns the subfield code length (position 11).</p>
     *
     * @return <code>char</code> - the subfield code length
     */
    public char getSubfieldCodeLength() {
	return subfieldCodeLength;
    }

    /**
     * <p>Returns the base address of data (positions 12-16).</p>
     *
     * @return <code>int</code> - the base address of data
     */
    public int getBaseAddressOfData() {
	return baseAddressOfData;
    }

    /**
     * <p>Returns implementation defined values
     * (positions 17-19).</p>
     *
     * @return <code>char</code> - implementation defined values
     */
    public char[] getImplDefined2() {
	return implDefined2;
    }

    /**
     * <p>Returns the entry map (positions 20-23).</p>
     *
     * @return <code>char[]</code> - the entry map
     */
    public char[] getEntryMap() {
	return entryMap;
    }

    /**
     * <p>Returns a String representation of the record label
     * following the MARC structure.</p>
     *
     * @return <code>String</code> - the record label
     */
    public String getSerializedForm() {
	return new StringBuffer()
	    .append(df.format(recordLength).toString())
	    .append(recordStatus)
	    .append(typeOfRecord)
	    .append(implDefined1)
	    .append(charCodingScheme)
	    .append(indicatorCount)
	    .append(subfieldCodeLength)
	    .append(df.format(baseAddressOfData).toString())
	    .append(implDefined2)
	    .append(entryMap)
	    .toString();
    }

    public String toString(){
        return getSerializedForm();
    }
    
}

