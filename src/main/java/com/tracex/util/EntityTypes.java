package com.tracex.util;

import java.util.List;

/** Node types stored in the graph. Used as the "type" property of every Entity node. */
public final class EntityTypes {

    public static final String PERSON = "PERSON";
    public static final String PHONE = "PHONE";
    public static final String VEHICLE = "VEHICLE";
    public static final String ACCOUNT = "ACCOUNT";
    public static final String LOCATION = "LOCATION";
    public static final String ALL = "ALL";

    /** Values accepted by the ranking page filter. Places are not scored, so they are not offered. */
    public static final List<String> FILTERS = List.of(PERSON, PHONE, VEHICLE, ACCOUNT, ALL);

    private EntityTypes() {
    }
}
