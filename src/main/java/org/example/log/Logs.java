package org.example.log;

import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Logs {

    static {
        try (InputStream stream = Logs.class.getClassLoader().getResourceAsStream("logging.properties")) {
            if (stream != null) {
                LogManager.getLogManager().readConfiguration(stream);
            }
        } catch (Exception e) {
            // noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }


}
