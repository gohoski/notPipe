package io.github.gohoski.notpipe.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Gleb on 29.01.2026
 * Utility class for loading images from URLs with optional memory caching.
 *
 * The cache is bounded both by entry count and by approximate pixel bytes so
 * that opening several videos in a row cannot exhaust the heap. Pending
 * requests keep only weak references to their views, so a destroyed activity
 * is never pinned by in-flight loads.
 */
public class ImageLoader {
    private static final Hashtable<String, Bitmap> memoryCache = new Hashtable<String, Bitmap>();
    private static final LinkedList<String> cacheHistory = new LinkedList<String>();
    private static final Hashtable<String, List<ImageRequest>> pendingViews = new Hashtable<String, List<ImageRequest>>();

    private static final int MAX_CACHE_SIZE = 16;
    private static final long MAX_CACHE_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_THUMB_DIM = 480;
    private static final int MAX_AVATAR_DIM = 96;
    private static long cacheBytes = 0;
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());

    interface ImageLoaderCallback {
        void onFail();
    }

    static class ImageRequest {
        private final WeakReference<ImageView> viewRef;
        final ImageLoaderCallback callback;

        ImageRequest(ImageView view, ImageLoaderCallback callback) {
            this.viewRef = view != null ? new WeakReference<ImageView>(view) : null;
            this.callback = callback;
        }

        public ImageView getView() {
            return viewRef != null ? (ImageView) viewRef.get() : null;
        }
    }

    /**
     * Load an image from URL into an ImageView with caching enabled.
     * Best for ListViews where images are recycled.
     */
    public static void loadImage(final String url, final ImageView imageView) {
        loadImage(url, imageView, true, null, MAX_THUMB_DIM);
    }

    public static synchronized void loadImage(final String url, final ImageView imageView, final boolean useCache) {
        loadImage(url, imageView, useCache, null, MAX_THUMB_DIM);
    }

    /**
     * Load an image from URL into an ImageView.
     * @param url The image URL
     * @param imageView The ImageView to display the image in
     * @param useCache Whether to use memory caching
     * @param callback Callback to listen for load events
     */
    public static synchronized void loadImage(final String url, final ImageView imageView, final boolean useCache, final ImageLoaderCallback callback) {
        loadImage(url, imageView, useCache, callback, MAX_THUMB_DIM);
    }

    /**
     * Load an image from URL into an ImageView, decoded to fit within maxDim.
     * @param url The image URL
     * @param imageView The ImageView to display the image in
     * @param useCache Whether to use memory caching
     * @param callback Callback to listen for load events
     * @param maxDim Maximum width/height of the decoded bitmap in pixels
     */
    private static synchronized void loadImage(final String url, final ImageView imageView, final boolean useCache, final ImageLoaderCallback callback, final int maxDim) {
        if (url == null || url.length() == 0) {
            if (callback != null) {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFail();
                    }
                });
            }
            return;
        }
        if (imageView != null) {
            imageView.setTag(url);
        }
        if (useCache && memoryCache.containsKey(url)) {
            Bitmap cachedBitmap = memoryCache.get(url);
            if (imageView != null && cachedBitmap != null && !cachedBitmap.isRecycled()) {
                imageView.setImageBitmap(cachedBitmap);
            } else if (imageView != null && cachedBitmap != null && cachedBitmap.isRecycled()) {
                memoryCache.remove(url);
                cacheHistory.remove(url);
            }
            return;
        }
        if (pendingViews.containsKey(url)) {
            pendingViews.get(url).add(new ImageRequest(imageView, callback));
            return;
        }
        final List<ImageRequest> views = new ArrayList<ImageRequest>();
        views.add(new ImageRequest(imageView, callback));
        pendingViews.put(url, views);
        executor.execute(new ImageLoadRunnable(url, useCache, maxDim));
    }

    /**
     * Load a small channel avatar into an ImageView with caching enabled.
     * Avatars are decoded at a fraction of full thumbnail size.
     */
    public static void loadAvatar(final String url, final ImageView imageView) {
        loadAvatar(url, imageView, null);
    }

    /**
     * Load a small channel avatar into an ImageView with caching enabled.
     * @param url The image URL
     * @param imageView The ImageView to display the image in
     * @param callback Callback to listen for load events
     */
    static void loadAvatar(final String url, final ImageView imageView, final ImageLoaderCallback callback) {
        loadImage(url, imageView, true, callback, MAX_AVATAR_DIM);
    }

    private static class ImageLoadRunnable implements Runnable {
        private final String url;
        private final boolean useCache;
        private final int maxDim;

        ImageLoadRunnable(String url, boolean useCache, int maxDim) {
            this.url = url;
            this.useCache = useCache;
            this.maxDim = maxDim;
        }

        @Override
        public void run() {
            final int MAX_RETRIES = 2;
            byte[] data = null;

            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                HttpURLConnection conn = null;
                InputStream is = null;
                ByteArrayOutputStream bos = null;
                try {
                    URL aURL = new URL(url);
                    conn = (HttpURLConnection) aURL.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(10000);
                    conn.connect();
                    is = conn.getInputStream();
                    bos = new ByteArrayOutputStream(32 * 1024);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                    }
                    data = bos.toByteArray();
                    break;
                } catch (java.net.SocketTimeoutException e) {
                    Log.w("ImageLoader", "Timeout on attempt " + (attempt + 1) + " for " + url);
                    if (attempt == MAX_RETRIES) {
                        Log.e("ImageLoader", "Final attempt failed. Giving up.");
                    } else {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    }
                } catch (OutOfMemoryError e) {
                    Log.e("ImageLoader", "OutOfMemoryError downloading " + url + ", freeing cache");
                    freeOldestHalf();
                    if (getCacheSize() == 0) break;
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    if (attempt == MAX_RETRIES) break;
                } catch (Exception e) {
                    Log.e("ImageLoader", "Download Error: " + e.getMessage());
                    break;
                } finally {
                    if (is != null) {
                        try { is.close(); } catch (Exception ignored) {}
                    }
                    if (bos != null) {
                        try { bos.close(); } catch (Exception ignored) {}
                    }
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
            Bitmap bmp = null;
            if (data != null) {
                bmp = decodeSampledWithRetry(data, url, maxDim);
                data = null;
            }
            final Bitmap finalBmp = bmp;
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    List<ImageRequest> pendingList;
                    synchronized (ImageLoader.class) {
                        List<ImageRequest> stored = pendingViews.get(url);
                        if (stored == null) {
                            return;
                        }
                        pendingList = new ArrayList<ImageRequest>(stored);
                        pendingViews.remove(url);
                    }

                    if (finalBmp != null) {
                        if (useCache && !finalBmp.isRecycled()) {
                            putInCache(url, finalBmp);
                        }

                        for (ImageRequest req : pendingList) {
                            ImageView view = req.getView();
                            if (view != null && !finalBmp.isRecycled()) {
                                String currentTag = (String) view.getTag();
                                if (url.equals(currentTag)) {
                                    view.setImageBitmap(finalBmp);
                                }
                            }
                        }
                    } else {
                        for (ImageRequest req : pendingList) {
                            if (req.callback != null) {
                                req.callback.onFail();
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * Decodes downloaded bytes to a downsampled bitmap fitting maxDim.
     * On OutOfMemoryError the oldest half of the shared cache is evicted and
     * decoding is retried with a larger sample size.
     */
    private static Bitmap decodeSampledWithRetry(byte[] data, String url, int maxDim) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        } catch (OutOfMemoryError e) {
            Log.e("ImageLoader", "OutOfMemoryError reading bounds for " + url);
            freeOldestHalf();
            return null;
        }
        int baseSample = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDim);
        Bitmap bmp = null;
        for (int retry = 0; retry <= 3; retry++) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = baseSample * (1 << retry);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            // set inPurgeable & inInputShareable via reflection (added in api 4)
            try {
                BitmapFactory.Options.class.getField("inPurgeable").setBoolean(opts, true);
                BitmapFactory.Options.class.getField("inInputShareable").setBoolean(opts, true);
            } catch (Throwable ignored) {}
            try {
                bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                break;
            } catch (OutOfMemoryError e) {
                Log.e("ImageLoader", "OutOfMemoryError loading " + url + ", freeing cache");
                freeOldestHalf();
                if (getCacheSize() == 0) break;
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                Log.e("ImageLoader", "Decode Error: " + e.getMessage());
                break;
            }
        }
        return bmp;
    }

    private static int computeSampleSize(int width, int height, int maxDim) {
        int sample = 1;
        while ((width / sample) > maxDim || (height / sample) > maxDim) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * Estimates the in-memory size of a bitmap without relying on
     * APIs unavailable on legacy devices.
     */
    private static int bitmapSize(Bitmap bmp) {
        if (bmp == null) {
            return 0;
        }
        try {
            int rowBytes = bmp.getRowBytes();
            int height = bmp.getHeight();
            if (rowBytes > 0 && height > 0) {
                return rowBytes * height;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * Stores a bitmap in the shared cache, evicting the oldest entries
     * while the cache exceeds its count or byte budget. Evicted bitmaps
     * are recycled so their native pixel memory is freed immediately
     * instead of waiting for the finalizer.
     */
    private static void putInCache(String url, Bitmap bmp) {
        synchronized (ImageLoader.class) {
            if (cacheHistory.contains(url)) {
                cacheHistory.remove(url);
                Bitmap old = memoryCache.remove(url);
                if (old != null && old != bmp) {
                    cacheBytes -= bitmapSize(old);
                }
            }
            cacheHistory.addLast(url);
            memoryCache.put(url, bmp);
            cacheBytes += bitmapSize(bmp);
            trimCache();
        }
    }

    private static void trimCache() {
        synchronized (ImageLoader.class) {
            while (!cacheHistory.isEmpty()
                    && (cacheHistory.size() > MAX_CACHE_SIZE || cacheBytes > MAX_CACHE_BYTES)) {
                String oldest = cacheHistory.removeFirst();
                Bitmap bmp = memoryCache.remove(oldest);
                if (bmp != null) {
                    cacheBytes -= bitmapSize(bmp);
                }
            }
            if (cacheBytes < 0) {
                cacheBytes = 0;
            }
        }
    }

    private static void freeOldestHalf() {
        synchronized (ImageLoader.class) {
            int toEvict = cacheHistory.size() / 2 + 1;
            for (int i = 0; i < toEvict; i++) {
                if (cacheHistory.isEmpty()) {
                    break;
                }
                String oldest = cacheHistory.removeFirst();
                Bitmap bmp = memoryCache.remove(oldest);
                if (bmp != null) {
                    cacheBytes -= bitmapSize(bmp);
                }
            }
            if (cacheBytes < 0) {
                cacheBytes = 0;
            }
        }
        System.gc();
        System.runFinalization();
    }

    /**
     * Recycles a bitmap, freeing its native pixel memory immediately.
     * Callers must guarantee no live view is still displaying it.
     */
    private static void recycleBitmap(Bitmap bmp) {
        if (bmp == null) {
            return;
        }
        try {
            if (!bmp.isRecycled()) {
                bmp.recycle();
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Returns true if the bitmap is still held by the shared cache.
     */
    private static boolean isBitmapCached(Bitmap bmp) {
        if (bmp == null) {
            return false;
        }
        synchronized (ImageLoader.class) {
            Enumeration<Bitmap> elements = memoryCache.elements();
            while (elements.hasMoreElements()) {
                if (elements.nextElement() == bmp) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Extracts the bitmap currently attached to a view, or null.
     */
    private static Bitmap getBitmapFromView(ImageView imageView) {
        if (imageView == null) {
            return null;
        }
        try {
            Drawable drawable = imageView.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Detaches a view and recycles its bitmap when no other owner needs it.
     * Bitmaps still held by the shared cache are left alone because other
     * views may display the same instance.
     */
    public static void unbindImageView(ImageView imageView) {
        if (imageView == null) {
            return;
        }
        cancelLoad(imageView);
        try {
            imageView.setImageBitmap(null);
        } catch (Throwable ignored) {}
    }

    /**
     * Preload an image into cache without displaying it.
     */
    public static void preloadImage(final String url) {
        loadImage(url, null, true, null, MAX_THUMB_DIM);
    }

    /**
     * Cancel loading for a specific ImageView.
     * Call this when the view scrolls off-screen or is recycled.
     */
    public static void cancelLoad(ImageView imageView) {
        if (imageView == null) {
            return;
        }
        imageView.setTag(null);
        synchronized (ImageLoader.class) {
            Enumeration<String> keys = pendingViews.keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                List<ImageRequest> list = pendingViews.get(key);
                if (list == null) {
                    continue;
                }
                for (int i = list.size() - 1; i >= 0; i--) {
                    ImageView view = list.get(i).getView();
                    if (view == null || view == imageView) {
                        list.remove(i);
                    }
                }
                if (list.isEmpty()) {
                    pendingViews.remove(key);
                }
            }
        }
    }

    /**
     * Cancel any pending load for the view and detach its bitmap so the
     * hosting activity can be collected. Cached bitmaps stay in the shared
     * cache for other activities to reuse.
     */
    public static void releaseView(ImageView imageView) {
        if (imageView == null) {
            return;
        }
        cancelLoad(imageView);
        try {
            imageView.setImageBitmap(null);
        } catch (Exception ignored) {}
    }

    /**
     * Cancel all pending image loads.
     * Useful when clearing a list.
     */
    public static void cancelAllLoads() {
        synchronized (ImageLoader.class) {
            pendingViews.clear();
        }
    }

    /**
     * Clear the memory cache to free memory.
     * Bitmaps still displayed by live views are left for the garbage
     * collector instead of being recycled, so this never invalidates
     * drawables that are still on screen.
     */
    public static void clearCache() {
        synchronized (ImageLoader.class) {
            memoryCache.clear();
            cacheHistory.clear();
            cacheBytes = 0;
        }
        System.gc();
    }

    /**
     * Get current cache size.
     */
    private static int getCacheSize() {
        return memoryCache.size();
    }
}
