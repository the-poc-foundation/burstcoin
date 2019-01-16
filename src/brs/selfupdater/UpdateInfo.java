package brs.selfupdater;

import brs.Version;
import org.json.simple.JSONObject;

public class UpdateInfo {
    private final Release latestAlpha;
    private final Release latestBeta;
    private final Release latestStable;
    private final Release latestUltrastable;

    public UpdateInfo(Release latestAlpha, Release latestBeta, Release latestStable, Release latestUltrastable) {
        this.latestAlpha = latestAlpha;
        this.latestBeta = latestBeta;
        this.latestStable = latestStable;
        this.latestUltrastable = latestUltrastable;
    }

    public Release getLatestAlpha() {
        return latestAlpha;
    }

    public Release getLatestBeta() {
        return latestBeta;
    }

    public Release getLatestStable() {
        return latestStable;
    }

    public Release getLatestUltrastable() {
        return latestUltrastable;
    }

    public static class Release {
        private final Version version;
        private final Version minimumPreviousVersion;
        private final String packageUrl;
        private final byte[] sha256;
        private final JSONObject signatures;

        public Release(Version version, Version minimumPreviousVersion, String packageUrl, byte[] sha256, JSONObject signatures) {
            this.version = version;
            this.minimumPreviousVersion = minimumPreviousVersion;
            this.packageUrl = packageUrl;
            this.sha256 = sha256;
            this.signatures = signatures;
        }

        public Version getVersion() {
            return version;
        }

        public Version getMinimumPreviousVersion() {
            return minimumPreviousVersion;
        }

        public String getPackageUrl() {
            return packageUrl;
        }

        public byte[] getSha256() {
            return sha256;
        }

        public JSONObject getSignatures() {
            return signatures;
        }
    }
}
