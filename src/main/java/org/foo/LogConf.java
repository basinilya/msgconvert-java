package org.foo;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public class LogConf {
    public LogConf() throws IOException {
        try (InputStream is = LogConf.class.getResourceAsStream("logger.properties")) {
            if (is != null) {
                LogManager.getLogManager().readConfiguration(is);
            }
        }
    }
}
