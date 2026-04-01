package com.tensura.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side store of citizenId → pokemon species mappings.
 * Updated via CitizenSpeciesSyncPacket from the server.
 * citizenId = ICitizenData.getId() — stable MineColonies integer, not entity network ID.
 */
public class CitizenSpeciesClient {

    private static Map<Integer, String> idToSpecies = Collections.emptyMap();

    public static void update(Map<Integer, String> data) {
        idToSpecies = new HashMap<>(data);
    }

    public static String getSpecies(int citizenId) {
        return idToSpecies.get(citizenId);
    }
}
