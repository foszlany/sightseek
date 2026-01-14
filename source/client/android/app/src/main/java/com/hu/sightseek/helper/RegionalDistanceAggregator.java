package com.hu.sightseek.helper;

import com.hu.sightseek.model.RegionalEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Helper class for aggregating distance from smallest region */
public class RegionalDistanceAggregator {
    /**
     * Aggregates distances based on smallest region.
     * @param entries List of RegionalEntries. Continent and country must be given. If smallRegion is defined, then largeRegion must also exist.
     * @return Map of regions and distances
     */
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

    /**
     * Adds new distance to map, creates entry if key doesn't exist.
     * @param distanceMap Map of regions and distances
     * @param key Region
     * @param newDistance Distance to add
     */
    private static void addDistance(Map<String, Double> distanceMap, String key, double newDistance) {
        if(newDistance == 0) {
            return;
        }

        if(!distanceMap.containsKey(key)) {
            distanceMap.put(key, newDistance);
        }
        else {
            distanceMap.compute(key, (k, oldDistance) -> (oldDistance == null ? newDistance : oldDistance + newDistance));
        }
    }
}
