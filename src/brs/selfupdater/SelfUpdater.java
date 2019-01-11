package brs.selfupdater;

import brs.Burst;
import brs.props.Props;
import brs.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Scanner;

public class SelfUpdater {
    public static final int UPDATING_EXIT_CODE = 8;
    private final Logger logger = LoggerFactory.getLogger(SelfUpdater.class);

    public static void start() {
        new Thread(() -> new SelfUpdater().startCheckingForUpdates()).start();
    }

    private void startCheckingForUpdates() {
        // Wait for BRS to load properties
        while (Burst.getPropertyService() == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Cleanup (in case we just updated)
        new File("update.sh").delete();
        new File("update.bat").delete();
        new File("update/new/").delete();

        if (!Burst.getPropertyService().getBoolean(Props.BRS_SELF_UPDATE)) {
            logger.info("Not starting auto-updater: Disabled in config.");
            return;
        }

        if (Burst.getPropertyService().getBoolean(Props.BRS_SELF_UPDATE_ANYTIME)) {
            logger.info("Starting self update thread!");
            new Thread(() -> {
                //noinspection InfiniteLoopStatement
                while(true) checkForUpdate(false);
            }).start();
        } else {
            logger.info("Not allowed to update anytime - will update once");
            checkForUpdate(true);
        }
    }

    private void checkForUpdate(boolean checkImmediately) {
        String url = Burst.getPropertyService().getString(Props.BRS_SELF_UPDATE_URL);
        if (!checkImmediately) waitToCheckForUpdate();
        logger.info("Getting update information from " + url);
        try {
            String updateInfoJson = new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
            System.out.println(updateInfoJson);
        } catch (Exception e) {
            logger.error("Could not get update information", e);
        }
    }

    private UpdateInfo getUpdateInfo(String url) {
        try {
            String updateInfoJson = new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
            JSONObject jsonObject = ((JSONObject) new JSONParser().parse(updateInfoJson));
            JSONObject alpha = (JSONObject) jsonObject.get("alpha");
            String alphaVersion = (String) alpha.get("version");
            String alphaPackage = (String) alpha.get("package");
            JSONObject beta = (JSONObject) jsonObject.get("beta");
            String betaVersion = (String) beta.get("version");
            String betaPackage = (String) beta.get("package");
            JSONObject stable = (JSONObject) jsonObject.get("stable");
            String stableVersion = (String) stable.get("version");
            String stablePackage = (String) stable.get("package");
            JSONObject ultrastable = (JSONObject) jsonObject.get("ultrastable");
            String ultrastableVersion = (String) ultrastable.get("version");
            String ultrastablePackage = (String) ultrastable.get("package");

            return new UpdateInfo(
                    new UpdateInfo.Version(alphaVersion, alphaPackage),
                    new UpdateInfo.Version(betaVersion, betaPackage),
                    new UpdateInfo.Version(stableVersion, stablePackage),
                    new UpdateInfo.Version(ultrastableVersion, ultrastablePackage)
            );
        } catch (Exception e) {
            logger.error("Could not fetch update info", e);
            return null;
        }
    }

    private void waitToCheckForUpdate() {
        try {
            Thread.sleep(randomDelayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int randomDelayMillis() {
        int oneDayMillis = 24 * 60 * 60 * 1000;
        int minimumDelayMillis = 6 * 60 * 60 * 1000;
        return new SecureRandom().nextInt(oneDayMillis - minimumDelayMillis) + minimumDelayMillis; // 6 to 24 hours
    }
}
