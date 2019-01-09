package brs;

import brs.props.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BurstLauncher {
    public static void main(String[] args) {
        new Thread(BurstLauncher::startCheckingForUpdates).start();
        try {
            Class.forName("javafx.application.Application");
            BurstGUI.main(args);
        } catch (ClassNotFoundException e) {
            LoggerFactory.getLogger(BurstLauncher.class).error("Could not start GUI as your JRE does not seem to have JavaFX installed. To install please install the \"openjfx\" package (eg. \"sudo apt install openjfx\")");
            Burst.main(args);
        }
    }

    private static void startCheckingForUpdates() {
        Logger logger = LoggerFactory.getLogger(BurstLauncher.class);

        while (Burst.getPropertyService() == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!Burst.getPropertyService().getBoolean(Props.BRS_SELF_UPDATE)) {
            logger.info("Not starting auto-updater; Disabled in config.");
            return;
        }

        logger.info("Starting self update thread!");


    }

    private static void checkForUpdate() {
        String url = Burst.getPropertyService().getString(Props.BRS_SELF_UPDATE_URL);
    }
}
