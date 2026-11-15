package io.github.gohoski.notpipe.util;

import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.Metadata;

/**
 * Created by Gleb on 05.06.2026.
 * Shared resolver for channel icons that need to be fetched by channel ID
 * when a video item is missing a channel thumbnail URL.
 * The state is kept statically so that results are reused across activities.
 */
public class ChannelIconResolver {
    public static final String FAILED = "FAILED";

    private static final Hashtable<String, String> resolvedChannelIcons = new Hashtable<String, String>();
    private static final Hashtable<String, Boolean> fetchingChannelIcons = new Hashtable<String, Boolean>();
    private static final ExecutorService channelIconExecutor = Executors.newFixedThreadPool(4);

    public interface OnIconResolvedListener {
        /**
         * Callback invoked on the main thread when a background icon fetch finishes.
         *
         * @param channelId the channel ID that was resolved
         * @param url the resolved icon URL, or null if resolution failed
         */
        void onIconResolved(String channelId, String url);
    }

    /**
     * Returns the previously resolved icon URL for a channel, or null if not yet resolved.
     * Returns the {@link #FAILED} constant if resolution previously failed.
     */
    public static String getResolved(String channelId) {
        if (channelId == null) return null;
        return resolvedChannelIcons.get(channelId);
    }

    /**
     * Pre-populates the cache with a known icon URL so that future
     * {@link #getResolved} calls can return it without a network fetch.
     */
    public static void setResolved(String channelId, String url) {
        if (channelId == null || url == null) return;
        if (url.length() == 0) return;
        resolvedChannelIcons.put(channelId, url);
    }

    /**
     * Loads a channel icon into the given view, preferring an icon URL that was
     * previously resolved for the channel. When no usable URL is available or
     * loading fails, a background fallback fetch by channel ID is requested via
     * {@link #requestFallback} and {@code resolveListener} is notified on completion.
     *
     * @param view the target image view
     * @param channelId the channel ID used for fallback resolution, may be null
     * @param thumbnailUrl the directly known icon URL, may be null
     * @param resolveListener optional listener notified when a fallback fetch completes
     */
    public static void loadInto(final ImageView view, final String channelId, final String thumbnailUrl, final OnIconResolvedListener resolveListener) {
        String url = thumbnailUrl;
        if (channelId != null && channelId.length() > 0) {
            String resolved = getResolved(channelId);
            if (FAILED.equals(resolved)) return;
            if (resolved != null) url = resolved;
        }
        if (url == null || url.length() == 0) {
            requestFallback(channelId, resolveListener);
        } else {
            ImageLoader.loadAvatar(url, view, new ImageLoader.ImageLoaderCallback() {
                @Override
                public void onFail() {
                    if (getResolved(channelId) == null) {
                        requestFallback(channelId, resolveListener);
                    }
                }
            });
        }
    }

    /**
     * Requests a background fetch of the channel icon.
     * If the icon is already resolved or being fetched, this is a no-op
     * and the listener is not invoked.
     * The listener is invoked on the main thread when the fetch completes.
     *
     * @param channelId the channel ID to resolve
     * @param listener  optional listener to be notified when the fetch completes
     */
    public static void requestFallback(final String channelId, final OnIconResolvedListener listener) {
        if (channelId == null || channelId.length() == 0
                || fetchingChannelIcons.containsKey(channelId)
                || resolvedChannelIcons.containsKey(channelId)) {
            return;
        }
        fetchingChannelIcons.put(channelId, true);
        channelIconExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String fetchedUrl = null;
                try {
                    Metadata fallbackApi = Manager.getInstance().getRandomMetadata();
                    if (fallbackApi != null) {
                        fetchedUrl = fallbackApi.getChannelIcon(channelId);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final String resultUrl = fetchedUrl;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        fetchingChannelIcons.remove(channelId);
                        if (resultUrl != null && resultUrl.length() > 0) {
                            resolvedChannelIcons.put(channelId, resultUrl);
                        } else {
                            resolvedChannelIcons.put(channelId, FAILED);
                        }
                        if (listener != null) {
                            listener.onIconResolved(channelId, resultUrl);
                        }
                    }
                });
            }
        });
    }
}