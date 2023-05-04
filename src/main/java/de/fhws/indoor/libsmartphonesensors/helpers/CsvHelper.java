package de.fhws.indoor.libsmartphonesensors.helpers;

public class CsvHelper {

    public static String getParameter(String csvStr, char separator, int idx) {
        int curSepIdx = 0, nextSepIdx = 0;
        for(int i = 0; i <= idx; ++i) {
            curSepIdx = nextSepIdx + 1;
            nextSepIdx = csvStr.indexOf(separator, curSepIdx);
            if(nextSepIdx == -1) {
                nextSepIdx = csvStr.length();
                if(i != idx) {
                    throw new IllegalArgumentException("Out of range");
                }
            }
        }
        return csvStr.substring(curSepIdx, nextSepIdx);
    }

}
