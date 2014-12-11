/*
 * Created on 21/Fev/2006
 *
 */
package pt.utl.ist.repox.recordPackage;

import org.dom4j.Element;



/** Represents a record in REPOX. These may be generic XML records or more concrete implementations that have an XML representation and a Java class that represent it.
 * Know Implementations are: RecordRepoxXpathId, RecordRepoxExternalId, RecordRepoxMarc
 *
 * @author Nuno Freire
 *
 */
public interface RecordRepox {

	/**
	 * Returns a representation of the record in DOM
	 */
	public Element getDom();

	/**
	 * @return the Id of the record
	 */
	public Object getId();

	/** Serializes the record into a byte array
	 * @return
	 * @throws Exception
	 */
	public byte[] serialize() throws Exception;


	/** Deserializes the record from a byte array
	 * @param bytes
	 * @throws Exception
	 */
	public void deserialize(byte[] bytes) throws Exception;

	/**
	 * Sets the deleted bit to isDeleted
	 * @param isDeleted
	 */
	public void setDeleted(boolean isDeleted);

	/**
	 * @return true if record is deleted, false otherwise
	 */
	public boolean isDeleted();
}
