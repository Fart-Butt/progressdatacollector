package org.fartmans.buttbotdatacollection;

public class DataSecrets {
    public static final String HOST = System.getenv("MC_DB_HOST");
    public static final String PORT = System.getenv("MC_DB_PORT");
    public static final String DATABASE = System.getenv("MC_DB_NAME");
    public static final String USER = System.getenv("MC_DB_USER");
    public static final String PASSWORD = System.getenv("MC_DB_PASSWORD");

    public static boolean isConfigured() {
        return HOST != null && DATABASE != null && USER != null && PASSWORD != null;
    }

    public static int getPort() {
        try {
            return PORT != null ? Integer.parseInt(PORT) : 3306;
        } catch (NumberFormatException e) {
            return 3306;
        }
    }
}