package com.hu.sightseek.helpers;

import com.hu.sightseek.model.RegionalEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionalDistanceAggregator {
    public static Map<String, Double> aggregateDistances(List<RegionalEntry> entries) {
        Map<String, Double> distanceMap = new HashMap<>();

        for(RegionalEntry entry : entries) {
            String continent = entry.getContinent();
            String country = entry.getCountry();
            String largeRegion = entry.getLargeRegion();
            String smallRegion = entry.getSmallRegion();
            double distance = entry.getDistance();

            // Global
            addDistance(distanceMap, "Global", distance);

            // Continent
            addDistance(distanceMap, continent, distance);

            // Country
            addDistance(distanceMap, continent + ";" + country, distance);

            if(largeRegion != null) {
                // Large region
                addDistance(distanceMap, continent + ";" + country + ";" + largeRegion, distance);

                // Small region
                if(smallRegion != null) {
                    addDistance(distanceMap, continent + ";" + country + ";" + largeRegion + ";" + smallRegion, distance);
                }
            }
        }

        return distanceMap;
    }

    private static void addDistance(Map<String, Double> distanceMap, String key, double newDistance) {
        if(newDistance == 0) {
            return;
        }

        if(!distanceMap.containsKey(key)) {
            distanceMap.put(key, newDistance);
        }
        else {
            Double oldDistance = distanceMap.get(key);
            distanceMap.put(key, (oldDistance == null ? newDistance : oldDistance + newDistance));
        }
    }
}
