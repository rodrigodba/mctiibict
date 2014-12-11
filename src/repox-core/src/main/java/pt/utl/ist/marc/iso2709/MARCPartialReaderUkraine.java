/**
 * MARCReader.java Version 0.2, November 2001
 *
 * Copyright (C) 2001  Bas Peters (mail@bpeters.com)
 *
 * This file is part of James (Java MARC Events).
 *
 * James is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * James is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with James; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package pt.utl.ist.marc.iso2709;

import org.apache.log4j.Logger;
import pt.utl.ist.marc.Record;
import pt.utl.ist.marc.xml.MarcWriterInXml;

import java.io.File;


public class MARCPartialReaderUkraine extends MARCPartialReader {
	/**
	 * Logger for this class
	 */
	private static final Logger log = Logger.getLogger(MARCPartialReaderUkraine.class);
    

	
    public MARCPartialReaderUkraine() {
    	super();
    }

    public MARCPartialReaderUkraine(String charset) {
    	super(charset);
    }
    
  
 
    
    
    public static void main(String[] args) throws Exception {
		MarcWriterInXml w=new MarcWriterInXml(new File("C:\\Desktop\\t.xml"));
		for (Record r: new IteratorIso2709Ukraine(new File("C:\\Desktop\\Projectos\\TELplus\\Repox\\ukraine.iso"))) {
			System.out.println(r.toMarcXChangeXmlString());
			
			
			w.write(r);
		}
		w.close();
		
		
		
	}
}
// End of MARCREader.java
