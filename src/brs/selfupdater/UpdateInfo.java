package brs.selfupdater;

import brs.Version;

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

        public Release(Version version, Version minimumPreviousVersion, String packageUrl, byte[] sha256) {
            this.version = version;
            this.minimumPreviousVersion = minimumPreviousVersion;
            this.packageUrl = packageUrl;
            this.sha256 = sha256;
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
    }
}
