package brs.selfupdater;

import brs.Burst;
import brs.Constants;
import brs.Version;
import brs.crypto.Crypto;
import brs.props.Props;
import brs.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

        // Cleanup (in case we just updated)c
        new File("update.sh").delete();
        new File("update.bat").delete();

        if (!Burst.getPropertyService().getBoolean(Props.BRS_SELF_UPDATER_ENABLE)) {
            logger.info("Not starting auto-updater: Disabled in config.");
            return;
        }

        if (Burst.getPropertyService().getBoolean(Props.BRS_SELF_UPDATER_ANYTIME)) {
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
        String url = Burst.getPropertyService().getString(Props.BRS_SELF_UPDATER_URL);
        if (!checkImmediately) waitToCheckForUpdate();
        logger.info("Getting update information from " + url);
        try {
            UpdateInfo updateInfo = getUpdateInfo(url);
            UpdateInfo.Release release;
            String channel = Burst.getPropertyService().getString(Props.BRS_SELF_UPDATER_CHANNEL);
            switch (channel) {
                case "alpha":
                    release = updateInfo.getLatestAlpha();
                    break;

                case "beta":
                    release = updateInfo.getLatestBeta();
                    break;

                case "stable":
                    release = updateInfo.getLatestStable();
                    break;

                case "ultrastable":
                    release = updateInfo.getLatestUltrastable();
                    break;

                default:
                    release = updateInfo.getLatestUltrastable();
                    break;
            }
            logger.info("Latest version available in the " + channel + " channel is " + release.getVersion() + ", must be at lease version " + release.getMinimumPreviousVersion() + " to update.");
            if (release.getVersion().isGreaterThan(Burst.VERSION) && Burst.VERSION.isGreaterThanOrEqualTo(release.getMinimumPreviousVersion())) {
                logger.warn("Updating from " + Burst.VERSION + " to " + release.getVersion() + "!!!");
                performUpdate(release);
            }
        } catch (Exception e) {
            logger.error("Could not self update", e);
        }
    }

    private UpdateInfo getUpdateInfo(String url) throws IOException, ParseException {
        String updateInfoJson = new Scanner(new URL(url).openStream(), "UTF-8").useDelimiter("\\A").next();
        JSONObject jsonObject = ((JSONObject) new JSONParser().parse(updateInfoJson));
        JSONObject alpha = (JSONObject) jsonObject.get("alpha");
        Version alphaVersion = Version.parse((String) alpha.get("version"));
        Version alphaMinimumPreviousVersion = Version.parse((String) alpha.get("minimumPreviousVersion"));
        String alphaPackage = (String) alpha.get("packageUrl");
        byte[] alphaSha256 = Convert.parseHexString((String) alpha.get("sha256"));
        JSONObject beta = (JSONObject) jsonObject.get("beta");
        Version betaVersion = Version.parse((String) beta.get("version"));
        Version betaMinimumPreviousVersion = Version.parse((String) beta.get("minimumPreviousVersion"));
        String betaPackage = (String) beta.get("packageUrl");
        byte[] betaSha256 = Convert.parseHexString((String) beta.get("sha256"));
        JSONObject stable = (JSONObject) jsonObject.get("stable");
        Version stableVersion = Version.parse((String) stable.get("version"));
        Version stableMinimumPreviousVersion = Version.parse((String) stable.get("minimumPreviousVersion"));
        String stablePackage = (String) stable.get("packageUrl");
        byte[] stableSha256 = Convert.parseHexString((String) stable.get("sha256"));
        JSONObject ultrastable = (JSONObject) jsonObject.get("ultrastable");
        Version ultrastableVersion = Version.parse((String) ultrastable.get("version"));
        Version ultrastableMinimumPreviousVersion = Version.parse((String) ultrastable.get("minimumPreviousVersion"));
        String ultrastablePackage = (String) ultrastable.get("packageUrl");
        byte[] ultrastableSha256 = Convert.parseHexString((String) ultrastable.get("sha256"));

        return new UpdateInfo(
                new UpdateInfo.Release(alphaVersion, alphaMinimumPreviousVersion, alphaPackage, alphaSha256),
                new UpdateInfo.Release(betaVersion, betaMinimumPreviousVersion, betaPackage, betaSha256),
                new UpdateInfo.Release(stableVersion, stableMinimumPreviousVersion, stablePackage, stableSha256),
                new UpdateInfo.Release(ultrastableVersion, ultrastableMinimumPreviousVersion, ultrastablePackage, ultrastableSha256)
        );
    }

    private void performUpdate(UpdateInfo.Release release) throws IOException {
        // Clear previous update
        File updateDir = new File(Constants.UPDATE_DIR);
        if (!updateDir.exists()) {
            updateDir.mkdirs();
        }
        String[] entries = updateDir.list();
        for(String s: entries){
            File currentFile = new File(updateDir.getPath(),s);
            currentFile.delete();
        }

        // Download file
        logger.info("Pulling update from " + release.getPackageUrl());
        try (InputStream inputStream = new URL(release.getPackageUrl()).openStream();
             OutputStream outputStream = new FileOutputStream(new File(Constants.UPDATE_DIR, "package.zip"))) {
            byte[] buffer = new byte[1024];
            int len;
            MessageDigest sha256 = Crypto.sha256();
            while ((len = inputStream.read(buffer)) > 0) {
                sha256.update(buffer, 0, len);
                outputStream.write(buffer, 0, len);
            }
            byte[] calculatedHash = sha256.digest();
            if (Arrays.equals(calculatedHash, release.getSha256())) {
                logger.info("Successfully pulled file with matching SHA-256 hash of " + Convert.toHexString(calculatedHash));
            } else {
                throw new IOException("SHA-256 hashes not equal. Calculated " + Convert.toHexString(calculatedHash) + " and expected " + Convert.toHexString(release.getSha256()));
            }
        }

        // Unzip
        logger.info("Unzipping update...");
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(new File(Constants.UPDATE_DIR, "package.zip")))) {
            byte[] buffer = new byte[1024];
            ZipEntry ze = zipInputStream.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(Constants.UPDATE_DIR, fileName);
                if (ze.isDirectory()) {
                    newFile.mkdirs();
                    ze = zipInputStream.getNextEntry();
                    continue;
                }
                logger.debug("Unzipping "+newFile.getAbsolutePath());
                try (OutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                if (newFile.getName().endsWith(".sh") || newFile.getName().endsWith(".bat") || newFile.getName().endsWith(".cmd")) {
                    newFile.setExecutable(true);
                }
                zipInputStream.closeEntry();
                ze = zipInputStream.getNextEntry();
            }
        }

        // Copy the update script from our resources
        String scriptFileName = "update" + (isWindows() ? ".bat" : ".sh");
        try (InputStream sourceFile = getClass().getResourceAsStream("/selfupdater/"+ scriptFileName);
             OutputStream targetFile = new FileOutputStream(scriptFileName)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = sourceFile.read(buffer)) > 0) {
                targetFile.write(buffer, 0, len);
            }
        }

        if (isWindows()) {
            Runtime.getRuntime().exec("cmd /c start update.bat");
        } else {
            Runtime.getRuntime().exec("nohup sh update.sh &");
        }

        // Say goodbye!
        logger.warn("Going down for self update... Wish me luck!");
        System.exit(UPDATING_EXIT_CODE);
    }

    public static void main(String[] args) {
        System.out.println(new SelfUpdater().isWindows());
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
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
