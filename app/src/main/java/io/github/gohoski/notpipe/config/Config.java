package io.github.gohoski.notpipe.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by opencode on 15.02.2026.
 * Data class holding user configuration settings.
 * Uses Java 1.5 compatible getters/setters (no Lombok/data classes).
 */
public class Config {
    public static final String DEFAULT_DOWNLOAD_FOLDER = "/sdcard/Download";
    private List<String> invidiousInstances;
    private List<String> yt2009Instances;
    private List<String> pipedInstances;
    private List<String> ytApiLegacyInstances;
    private List<String> typetypeInstances;
    private String preferredQuality;
    private boolean updateInstancesFromUrl;
    private String instancesUpdateUrl;
    private int updateFrequency;
    private boolean useExternalPlayer;
    private boolean streamPlayback;
    private long lastUpdate;
    private boolean convertVideos;
    private int convertCodec;
    private boolean asyncSetVideoUri;
    private boolean fullscreenRotateLandscape;
    private String downloadFolder;
    private boolean hideShortsRecommendations;
    private boolean hideShortsSubscriptions;
    private int shortLengthSeconds;

    Config() {
        invidiousInstances = new ArrayList<String>();
        yt2009Instances = new ArrayList<String>();
        pipedInstances = new ArrayList<String>();
        ytApiLegacyInstances = new ArrayList<String>();
        typetypeInstances = new ArrayList<String>();
        fullscreenRotateLandscape = true;
        downloadFolder = DEFAULT_DOWNLOAD_FOLDER;
//        hideShortsRecommendations = true;
//        hideShortsSubscriptions = true;
//        shortLengthSeconds = 90;
    }

    /**
     * Copy constructor for creating a defensive copy.
     */
    public Config(Config other) {
        invidiousInstances = new ArrayList<String>(other.invidiousInstances);
        yt2009Instances = new ArrayList<String>(other.yt2009Instances);
        pipedInstances = new ArrayList<String>(other.pipedInstances);
        ytApiLegacyInstances = new ArrayList<String>(other.ytApiLegacyInstances);
        typetypeInstances = new ArrayList<String>(other.typetypeInstances);
        preferredQuality = other.preferredQuality;
        updateInstancesFromUrl = other.updateInstancesFromUrl;
        instancesUpdateUrl = other.instancesUpdateUrl;
        updateFrequency = other.updateFrequency;
        useExternalPlayer = other.useExternalPlayer;
        streamPlayback = other.streamPlayback;
        lastUpdate = other.lastUpdate;
        convertVideos = other.convertVideos;
        convertCodec = other.convertCodec;
        asyncSetVideoUri = other.asyncSetVideoUri;
        fullscreenRotateLandscape = other.fullscreenRotateLandscape;
        downloadFolder = other.downloadFolder;
        hideShortsRecommendations = other.hideShortsRecommendations;
        hideShortsSubscriptions = other.hideShortsSubscriptions;
        shortLengthSeconds = other.shortLengthSeconds;
    }

    public List<String> getInvidiousInstances() {
        return invidiousInstances;
    }

    public void setInvidiousInstances(List<String> instances) {
        this.invidiousInstances = instances;
    }

    public List<String> getYt2009Instances() {
        return yt2009Instances;
    }

    public void setYt2009Instances(List<String> instances) {
        this.yt2009Instances = instances;
    }

    public List<String> getPipedInstances() {
        return pipedInstances;
    }

    public void setPipedInstances(List<String> instances) {
        this.pipedInstances = instances;
    }

    public List<String> getYtApiLegacyInstances() {
        return ytApiLegacyInstances;
    }

    public void setYtApiLegacyInstances(List<String> instances) {
        this.ytApiLegacyInstances = instances;
    }

    public List<String> getTypeTypeInstances() {
        return typetypeInstances;
    }

    public void setTypeTypeInstances(List<String> instances) {
        this.typetypeInstances = instances;
    }

    public String getPreferredQuality() {
        return preferredQuality;
    }

    public void setPreferredQuality(String quality) {
        this.preferredQuality = quality;
    }

    public boolean isUpdateInstancesFromUrl() {
        return updateInstancesFromUrl;
    }

    public void setUpdateInstancesFromUrl(boolean update) {
        this.updateInstancesFromUrl = update;
    }

    public String getInstancesUpdateUrl() {
        return instancesUpdateUrl;
    }

    public void setInstancesUpdateUrl(String url) {
        this.instancesUpdateUrl = url;
    }

    public int getUpdateFrequency() {
        return updateFrequency;
    }

    public void setUpdateFrequency(int frequency) {
        this.updateFrequency = frequency;
    }

    public boolean isUseExternalPlayer() {
        return useExternalPlayer;
    }

    public void setUseExternalPlayer(boolean external) {
        this.useExternalPlayer = external;
    }

    public boolean isStreamPlayback() {
        return streamPlayback;
    }

    public void setStreamPlayback(boolean stream) {
        this.streamPlayback = stream;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public boolean isConvertVideos() {
        return convertVideos;
    }

    public void setConvertVideos(boolean convertVideos) {
        this.convertVideos = convertVideos;
    }

    public int getConvertCodec() {
        return convertCodec;
    }

    public void setConvertCodec(int convertCodec) {
        this.convertCodec = convertCodec;
    }

    public boolean isAsyncSetVideoUri() {
        return asyncSetVideoUri;
    }

    public void setAsyncSetVideoUri(boolean asyncSetVideoUri) {
        this.asyncSetVideoUri = asyncSetVideoUri;
    }

    public boolean isFullscreenRotateLandscape() {
        return fullscreenRotateLandscape;
    }

    public void setFullscreenRotateLandscape(boolean rotate) {
        this.fullscreenRotateLandscape = rotate;
    }

    public String getDownloadFolder() {
        return downloadFolder;
    }

    public void setDownloadFolder(String folder) {
        this.downloadFolder = folder;
    }

    public boolean isHideShortsRecommendations() {
        return hideShortsRecommendations;
    }

    public void setHideShortsRecommendations(boolean hide) {
        this.hideShortsRecommendations = hide;
    }

    public boolean isHideShortsSubscriptions() {
        return hideShortsSubscriptions;
    }

    public void setHideShortsSubscriptions(boolean hide) {
        this.hideShortsSubscriptions = hide;
    }

    public int getShortLengthSeconds() {
        return shortLengthSeconds;
    }

    public void setShortLengthSeconds(int seconds) {
        this.shortLengthSeconds = seconds;
    }

    /**
     * Add default instance. Called by ConfigManager.ensureInstancesConfigured()
     * when all instance lists are empty.
     */
    public void applyDefaults() {
        ytApiLegacyInstances.add("http://45.132.96.44:2823");

        updateInstancesFromUrl = true;
        instancesUpdateUrl = "http://144.31.189.129/notPipe.json";
        updateFrequency = 1;
        streamPlayback = true;
        fullscreenRotateLandscape = true;
    }
}