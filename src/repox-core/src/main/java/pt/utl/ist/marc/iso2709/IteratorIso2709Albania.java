/*
 * Created on 29/Abr/2005
 *
 */
package pt.utl.ist.marc.iso2709;

import pt.utl.ist.marc.Record;
import pt.utl.ist.marc.xml.MarcWriterInXml;

import java.io.File;

public class IteratorIso2709Albania extends IteratorIso2709 {
	

	public IteratorIso2709Albania(File isoFile) {
		super(isoFile);
		MARCPartialReaderUkraine reader=new MARCPartialReaderUkraine("ISO8859-1");
		reader.setRecordTerminator('\\');
		reader.setFieldTerminator('^');
		isoNavig=new IsoNavigator(isoFile.getAbsolutePath(), reader);
	}
	
	public IteratorIso2709Albania(File isoFile, String charset) {
		super(isoFile, charset);
		MARCPartialReaderUkraine reader=new MARCPartialReaderUkraine(charset);
		reader.setRecordTerminator('\\');
		reader.setFieldTerminator('^');
//		reader.setUnitDelimiter('^');
		isoNavig=new IsoNavigator(isoFile.getAbsolutePath(), reader);
	}
	

    
    public static void main(String[] args) throws Exception {
		MarcWriterInXml w=new MarcWriterInXml(new File("C:\\Desktop\\t.xml"));
		for (Record r: new IteratorIso2709Albania(new File("C:\\Desktop\\Projectos\\TELplus\\Repox\\Albania.txt"))) {
			w.write(r);
			System.out.println(r);
		}
		w.close();
	}
}
