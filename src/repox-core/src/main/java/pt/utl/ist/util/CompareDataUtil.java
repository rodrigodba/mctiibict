package pt.utl.ist.util;

import pt.utl.ist.repox.dataProvider.DataProvider;
import pt.utl.ist.repox.dataProvider.DataSourceContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created to REPOX project.
 * User: Edmundo
 * Date: 02/04/12
 * Time: 17:20
 */
public class CompareDataUtil {

    public static List<DataSourceContainer> convertHashToList(HashMap<String,DataSourceContainer> containers){
        List<DataSourceContainer> dataSourceContainers = new ArrayList<DataSourceContainer>();
        for (DataSourceContainer dataSourceContainer : containers.values()) {
            dataSourceContainers.add(dataSourceContainer);
        }
        return dataSourceContainers;
    }

    public static class DPComparator implements java.util.Comparator<DataProvider>{
        public int compare(DataProvider dp1, DataProvider dp2){
            String str1 = dp1.getName().toUpperCase();
            String str2 = dp2.getName().toUpperCase();

            if(str1.compareTo(str2) < 0)
                return -1;
            else{
                if(str1.compareTo(str2) > 0)
                    return 1;
            }
            return 0;
        }
    }

    public static class DSComparator implements java.util.Comparator<DataSourceContainer>{
        public int compare(DataSourceContainer ds1, DataSourceContainer ds2){
            String str1 = ds1.getDataSource().getId().toUpperCase();
            String str2 = ds2.getDataSource().getId().toUpperCase();

            if(str1.compareTo(str2) < 0)
                return -1;
            else{
                if(str1.compareTo(str2) > 0)
                    return 1;
            }
            return 0;
        }
    }
}
