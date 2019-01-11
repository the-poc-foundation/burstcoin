package brs.selfupdater;

public class UpdateInfo {
    private final Version latestAlpha;
    private final Version latestBeta;
    private final Version latestStable;
    private final Version latestUltrastable;

    public UpdateInfo(Version latestAlpha, Version latestBeta, Version latestStable, Version latestUltrastable) {
        this.latestAlpha = latestAlpha;
        this.latestBeta = latestBeta;
        this.latestStable = latestStable;
        this.latestUltrastable = latestUltrastable;
    }

    public Version getLatestAlpha() {
        return latestAlpha;
    }

    public Version getLatestBeta() {
        return latestBeta;
    }

    public Version getLatestStable() {
        return latestStable;
    }

    public Version getLatestUltrastable() {
        return latestUltrastable;
    }

    public static class Version {
        private final String version;
        private final String packageUrl;

        public Version(String version, String packageUrl) {
            this.version = version;
            this.packageUrl = packageUrl;
        }

        public String getVersion() {
            return version;
        }

        public String getPackageUrl() {
            return packageUrl;
        }
    }
}
