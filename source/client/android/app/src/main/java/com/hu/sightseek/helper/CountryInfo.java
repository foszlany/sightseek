package com.hu.sightseek.helper;

import java.util.HashMap;
import java.util.Map;

/** Provides basic information about countries based on ISO 3166 country codes */
public final class CountryInfo {
    /** Map of country codes and countries */
    private static final Map<String, String> countryMap;
    /** Map of country codes and continents */
    private static final Map<String, String> continentMap;

    /** Private constructor */
    private CountryInfo() {}

    // Add basic info about countries here. Everything else is read from shapefiles.
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

    /**
     * Gets country name based on country code.
     * @param countryCode Country code
     * @return Country name
     */
    public static String getCountry(String countryCode) {
        String code = countryCode.toLowerCase();

        if(!countryMap.containsKey(code)) {
            throw new UnsupportedOperationException("Country name doesn't exist for code: " + countryCode);
        }

        return countryMap.get(code);
    }

    /**
     * Gets continent name based on country code.
     * @param countryCode Country code
     * @return Continent name
     */
    public static String getContinent(String countryCode) {
        String code = countryCode.toLowerCase();

        if(!continentMap.containsKey(code)) {
            throw new UnsupportedOperationException("Continent name doesn't exist for code: " + countryCode);
        }

        return continentMap.get(code);
    }
}