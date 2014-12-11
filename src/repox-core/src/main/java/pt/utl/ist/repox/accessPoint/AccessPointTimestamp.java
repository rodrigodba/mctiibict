package pt.utl.ist.repox.accessPoint;

import pt.utl.ist.repox.recordPackage.RecordRepox;

import java.util.*;


/**
 * An accessPoint implementation that index the timestamp of a record
 * @author Nuno Freire
 *
 */
public class AccessPointTimestamp extends AccessPoint{

	public AccessPointTimestamp(String id) {
		super(id);
		tokenizable = false;
	}

	@Override
	public Collection<Date> index(RecordRepox record) {
		Set<Date> idxVals=new HashSet<Date>(1);
		idxVals.add(new Date());
		return idxVals;
	}

	@Override
	public List index(List<RecordRepox> records) {
		List<Date> idxVals=new ArrayList<Date>();

		Date currentDate = new Date();
		for (RecordRepox recordRepox : records) {
			idxVals.add(currentDate);
		}

		return idxVals;
	}

	@Override
	public Class typeOfIndex() {
		return Date.class;
	}

	public String getDescription() {
		return "Timestamp";
	}

}
