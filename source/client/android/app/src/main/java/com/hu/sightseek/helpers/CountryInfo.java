package com.hu.sightseek.helpers;

import java.util.HashMap;
import java.util.Map;

public final class CountryInfo {
    private static final Map<String, String> countryMap;
    private static final Map<String, String> continentMap;

    private CountryInfo() {}

    static {
        countryMap = new HashMap<>();
        continentMap = new HashMap<>();

        countryMap.put("li", "Liechtenstein");
        continentMap.put("li", "Europe");

        countryMap.put("hu", "Hungary");
        continentMap.put("hu", "Europe");

        countryMap.put("mu", "Mauritius");
        continentMap.put("mu", "Africa");
    }

    public static String getCountry(String countryCode) {
        String code = countryCode.toLowerCase();

        if(!countryMap.containsKey(code)) {
            throw new UnsupportedOperationException("Country name doesn't exist for code: " + countryCode);
        }

        return countryMap.get(code);
    }

    public static String getContinent(String countryCode) {
        String code = countryCode.toLowerCase();

        if(!continentMap.containsKey(code)) {
            throw new UnsupportedOperationException("Continent name doesn't exist for code: " + countryCode);
        }

        return continentMap.get(code);
    }
}