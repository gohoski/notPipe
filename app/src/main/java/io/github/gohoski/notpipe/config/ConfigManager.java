package io.github.gohoski.notpipe.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.NotPipe;
import io.github.gohoski.notpipe.Utils;

/**
 * Created by opencode on 15.02.2026.
 * Singleton manager for loading and saving user configuration.
 * Uses SharedPreferences for persistence.
 */
public class ConfigManager {
    private static final String PREFS_NAME = "notPipe";
    private static final String KEY_INVIDIOUS = "invidious";
    private static final String KEY_YT2009 = "yt2009";
    private static final String KEY_PIPED = "piped";
    private static final String KEY_YTAPILEGACY = "ytapilegacy";
    private static final String KEY_TYPETYPE = "typetype";
    private static final String KEY_PREFERRED_QUALITY = "preferred_quality";
    private static final String KEY_UPDATE_INSTANCES = "update_instances_from_url";
    private static final String KEY_INSTANCES_URL = "instances_update_url";
    private static final String KEY_UPDATE_FREQUENCY = "update_frequency";
    private static final String KEY_EXTERNAL_PLAYER = "external_player";
    private static final String KEY_STREAM_PLAYBACK = "stream_playback";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final String KEY_CONVERT_VIDEOS = "convert_videos";
    private static final String KEY_CONVERT_CODEC = "convert_codec";
    private static final String KEY_ASYNC_SET_VIDEO_URI = "async_set_video_uri";
    private static final String KEY_FULLSCREEN_ROTATE = "fullscreen_rotate_landscape";
    private static final String KEY_DOWNLOAD_FOLDER = "download_folder";
    private static final String KEY_HIDE_SHORTS_RECOMMENDATIONS = "hide_shorts_recommendations";
    private static final String KEY_HIDE_SHORTS_SUBSCRIPTIONS = "hide_shorts_subscriptions";
    private static final String KEY_SHORT_LENGTH_SECONDS = "short_length_seconds";

    private static final String SEPARATOR = ";";

    private static ConfigManager instance;
    private Config config;
    private SharedPreferences prefs;

    private ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadConfig();
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context);
        }
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager.init(Context) must be called first!");
        }
        return instance;
    }

    /**
     * Get a copy of the current configuration.
     */
    public Config getConfig() {
        return new Config(config);
    }

    /**
     * Save the given configuration to SharedPreferences.
     */
    public void saveConfig(Config config) {
        this.config = new Config(config);
        saveToPrefs();
    }

    private void saveToPrefs() {
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_INVIDIOUS, joinList(config.getInvidiousInstances()));
        editor.putString(KEY_YT2009, joinList(config.getYt2009Instances()));
        editor.putString(KEY_PIPED, joinList(config.getPipedInstances()));
        editor.putString(KEY_YTAPILEGACY, joinList(config.getYtApiLegacyInstances()));
        editor.putString(KEY_TYPETYPE, joinList(config.getTypeTypeInstances()));
        editor.putString(KEY_PREFERRED_QUALITY, config.getPreferredQuality());
        editor.putBoolean(KEY_UPDATE_INSTANCES, config.isUpdateInstancesFromUrl());
        editor.putString(KEY_INSTANCES_URL, config.getInstancesUpdateUrl());
        editor.putInt(KEY_UPDATE_FREQUENCY, config.getUpdateFrequency());
        editor.putBoolean(KEY_EXTERNAL_PLAYER, config.isUseExternalPlayer());
        editor.putBoolean(KEY_STREAM_PLAYBACK, config.isStreamPlayback());
        editor.putLong(KEY_LAST_UPDATE, config.getLastUpdate());
        editor.putBoolean(KEY_CONVERT_VIDEOS, config.isConvertVideos());
        editor.putInt(KEY_CONVERT_CODEC, config.getConvertCodec());
        editor.putBoolean(KEY_ASYNC_SET_VIDEO_URI, config.isAsyncSetVideoUri());
        editor.putBoolean(KEY_FULLSCREEN_ROTATE, config.isFullscreenRotateLandscape());
        editor.putBoolean(KEY_HIDE_SHORTS_RECOMMENDATIONS, config.isHideShortsRecommendations());
        editor.putBoolean(KEY_HIDE_SHORTS_SUBSCRIPTIONS, config.isHideShortsSubscriptions());
        editor.putInt(KEY_SHORT_LENGTH_SECONDS, config.getShortLengthSeconds());
        String downloadFolder = config.getDownloadFolder();
        if (downloadFolder == null || downloadFolder.trim().length() == 0) {
            downloadFolder = Config.DEFAULT_DOWNLOAD_FOLDER;
        }
        editor.putString(KEY_DOWNLOAD_FOLDER, downloadFolder);
        editor.commit();
    }

    /**
     * Load configuration from SharedPreferences
     */
    private void loadConfig() {
        config = new Config();

        String invidiousStr = prefs.getString(KEY_INVIDIOUS, "");
        if (invidiousStr.length() > 0) {
            config.setInvidiousInstances(parseList(invidiousStr));
        }

        String yt2009Str = prefs.getString(KEY_YT2009, "");
        if (yt2009Str.length() > 0) {
            config.setYt2009Instances(parseList(yt2009Str));
        }

        String pipedStr = prefs.getString(KEY_PIPED, "");
        if (pipedStr.length() > 0) {
            config.setPipedInstances(parseList(pipedStr));
        }

        String ytApiLegacyStr = prefs.getString(KEY_YTAPILEGACY, "");
        if (ytApiLegacyStr.length() > 0) {
            config.setYtApiLegacyInstances(parseList(ytApiLegacyStr));
        }

        String typetypeStr = prefs.getString(KEY_TYPETYPE, "");
        if (typetypeStr.length() > 0) {
            config.setTypeTypeInstances(parseList(typetypeStr));
        }

        config.setPreferredQuality(prefs.getString(KEY_PREFERRED_QUALITY, "360"));
        config.setUpdateInstancesFromUrl(prefs.getBoolean(KEY_UPDATE_INSTANCES, false));
        config.setInstancesUpdateUrl(prefs.getString(KEY_INSTANCES_URL, "http://144.31.189.129/notPipe.json"));
        config.setUpdateFrequency(prefs.getInt(KEY_UPDATE_FREQUENCY, 1));
        config.setUseExternalPlayer(prefs.getBoolean(KEY_EXTERNAL_PLAYER, false));
        config.setLastUpdate(prefs.getLong(KEY_LAST_UPDATE, 0L));
        config.setAsyncSetVideoUri(prefs.getBoolean(KEY_ASYNC_SET_VIDEO_URI, NotPipe.SDK >= 14 && NotPipe.SDK <= 18));
        config.setFullscreenRotateLandscape(prefs.getBoolean(KEY_FULLSCREEN_ROTATE, true));
        config.setHideShortsRecommendations(prefs.getBoolean(KEY_HIDE_SHORTS_RECOMMENDATIONS, true));
        config.setHideShortsSubscriptions(prefs.getBoolean(KEY_HIDE_SHORTS_SUBSCRIPTIONS, true));
        config.setShortLengthSeconds(prefs.getInt(KEY_SHORT_LENGTH_SECONDS, 90));

        String downloadFolder = prefs.getString(KEY_DOWNLOAD_FOLDER, Config.DEFAULT_DOWNLOAD_FOLDER);
        if (downloadFolder != null && downloadFolder.trim().length() > 0) {
            config.setDownloadFolder(downloadFolder.trim());
        }

        if (prefs.contains(KEY_CONVERT_VIDEOS)) {
            config.setStreamPlayback(prefs.getBoolean(KEY_STREAM_PLAYBACK, true));
            config.setConvertVideos(prefs.getBoolean(KEY_CONVERT_VIDEOS, false));
            config.setUseExternalPlayer(prefs.getBoolean(KEY_EXTERNAL_PLAYER, false));
            config.setConvertCodec(prefs.getInt(KEY_CONVERT_CODEC, 0));
        } else { // First time setup
            boolean convert, external = false;
            boolean notV7 = !Utils.isV7();
            if (NotPipe.SDK < 5 || (NotPipe.SDK < 11 && notV7)) {
                convert = true;
            } else {
                convert = false;
            }
            config.setConvertVideos(convert);
            config.setStreamPlayback(true);
            config.setUseExternalPlayer(external);
            config.setConvertCodec(0);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_CONVERT_VIDEOS, convert);
            editor.putBoolean(KEY_STREAM_PLAYBACK, true);
            editor.putBoolean(KEY_EXTERNAL_PLAYER, external);
            editor.putInt(KEY_CONVERT_CODEC, 0);
            editor.commit();
        }
    }

    public void resetToDefaults() {
        config = new Config();
        config.applyDefaults();
        saveToPrefs();
    }

    /**
     * Apply default instances if no instances are configured.
     */
    public void ensureInstancesConfigured() {
        if (config.getInvidiousInstances().isEmpty()
                && config.getYt2009Instances().isEmpty()
                && config.getPipedInstances().isEmpty()
                && config.getYtApiLegacyInstances().isEmpty()
                && config.getTypeTypeInstances().isEmpty()) {
            config.applyDefaults();
            saveToPrefs();
        }
    }

    private List<String> parseList(String value) {
        List<String> result = new ArrayList<String>();
        if (value == null || value.length() == 0) {
            return result;
        }
        String[] parts = value.split(SEPARATOR);
        for (int i = 0; i < parts.length; i++) {
            String trimmed = parts[i].trim();
            if (trimmed.length() > 0) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String joinList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}