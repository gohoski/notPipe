package io.github.gohoski.notpipe;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;

/**
 * Created by Gleb on 15.11.2026.
 * Chooses where downloaded videos are kept and resolves cached files.
 * External storage is preferred when it is mounted and writable; otherwise
 * videos are stored in the app's internal cache, so devices without an SD
 * card (or with a large internal storage) can use non-streaming playback.
 */
final class VideoStorage {
    private static final String EXTERNAL_DIR = "notPipe/videos";
    private static final String INTERNAL_DIR = "videos";

    /**
     * Minimum free space required before a video is downloaded. Kept low
     * enough for devices with modest internal storage.
     */
    static final long MIN_FREE_BYTES = 64L * 1024L * 1024L;

    private VideoStorage() {
    }

    /**
     * Returns true when external storage is mounted.
     */
    public static boolean isExternalAvailable() {
        try {
            return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        } catch (Exception e) {
            return false;
        }
    }

    public static File getExternalDir() {
        return new File(Environment.getExternalStorageDirectory(), EXTERNAL_DIR);
    }

    public static File getInternalDir(Context context) {
        return new File(context.getCacheDir(), INTERNAL_DIR);
    }

    /**
     * Returns both cache directories in order of preference.
     */
    public static File[] getCacheDirs(Context context) {
        return new File[] { getExternalDir(), getInternalDir(context) };
    }

    /**
     * Looks for an already cached copy of the video in either storage location.
     *
     * @param context application or activity context
     * @param videoId YouTube video id
     * @return the cached file, or null when it has not been downloaded yet
     */
    public static File findExisting(Context context, String videoId) {
        String name = getFileName(videoId);
        File external = new File(getExternalDir(), name);
        if (external.exists() && external.isFile()) return external;
        File internal = new File(getInternalDir(context), name);
        if (internal.exists() && internal.isFile()) return internal;
        return null;
    }

    /**
     * Returns an existing cached file or the path where the video should be saved.
     *
     * @param context application or activity context
     * @param videoId YouTube video id
     * @return cached or destination file
     */
    public static File getVideoFile(Context context, String videoId) {
        File existing = findExisting(context, videoId);
        if (existing != null) return existing;
        return new File(getPreferredDir(context), getFileName(videoId));
    }

    /**
     * Returns the file used by {@link VideoContentProvider} for internal storage.
     */
    public static File getInternalFile(Context context, String videoId) {
        if (videoId == null || videoId.length() == 0) return null;
        return new File(getInternalDir(context), getFileName(videoId));
    }

    /**
     * Chooses a destination file, preferring external storage but falling back
     * to internal storage when no SD card is available or it is full.
     *
     * @param context application or activity context
     * @param videoId YouTube video id
     * @return destination file, or null when there is not enough free space
     */
    public static File getDownloadFile(Context context, String videoId) {
        File existing = findExisting(context, videoId);
        if (existing != null) return existing;

        File[] dirs = getCacheDirs(context);
        for (int i = 0; i < dirs.length; i++) {
            if (!ensureDir(dirs[i])) continue;
            if (getAvailableBytes(dirs[i]) >= MIN_FREE_BYTES) {
                return new File(dirs[i], getFileName(videoId));
            }
        }
        return null;
    }

    /**
     * Returns true when at least one cache directory can be written to.
     */
    public static boolean hasWritableStorage(Context context) {
        File[] dirs = getCacheDirs(context);
        for (int i = 0; i < dirs.length; i++) {
            if (ensureDir(dirs[i])) return true;
        }
        return false;
    }

    /**
     * Returns true for local file or content URIs.
     */
    public static boolean isLocal(String path) {
        if (path == null) return false;
        return path.startsWith("file://") || path.startsWith("content://");
    }

    /**
     * Returns true when the path or URI points to a cached video file.
     */
    public static boolean isCachedVideoPath(Context context, String path) {
        if (isLocal(path)) return true;
        if (path == null) return false;
        String external = getExternalDir().getAbsolutePath();
        String internal = getInternalDir(context).getAbsolutePath();
        return path.startsWith(external) || path.startsWith(internal);
    }

    /**
     * Converts a cached file path into a URI that VideoView or an external
     * player can open. App-private files are exposed through
     * {@link VideoContentProvider} instead of being made world-readable.
     *
     * @param context application or activity context
     * @param path local path or remote URL
     * @return playable URI
     */
    public static String toPlayableUri(Context context, String path) {
        if (path == null) return null;
        String internal = getInternalDir(context).getAbsolutePath();

        if (path.startsWith("file://")) {
            String filePath = Uri.parse(path).getPath();
            if (filePath != null && filePath.startsWith(internal)) {
                return getContentUri(filePath);
            }
            return path;
        }
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("content://")) {
            return path;
        }
        if (path.startsWith(internal)) {
            return getContentUri(path);
        }
        if (path.startsWith("/")) {
            return Uri.fromFile(new File(path)).toString();
        }
        return path;
    }

    /**
     * Returns the total size of all cached videos in bytes.
     */
    public static long getUsedBytes(Context context) {
        File[] dirs = getCacheDirs(context);
        long total = 0;
        for (int i = 0; i < dirs.length; i++) {
            total += getDirectorySize(dirs[i]);
        }
        return total;
    }

    /**
     * Deletes cached videos that have not been modified for the given period.
     * Used by the optional automatic cleanup so recently watched or downloaded
     * videos are left alone.
     *
     * @param context application or activity context
     * @param maxAgeMillis maximum allowed file age in milliseconds
     * @return number of bytes freed
     */
    public static long clearOlderThan(Context context, long maxAgeMillis) {
        long now = System.currentTimeMillis();
        long freed = 0;
        File[] dirs = getCacheDirs(context);
        for (int i = 0; i < dirs.length; i++) {
            File[] files = dirs[i].listFiles();
            if (files == null) continue;
            for (int j = 0; j < files.length; j++) {
                if (!files[j].isFile()) continue;
                long modified = files[j].lastModified();
                if (modified > 0 && now - modified < maxAgeMillis) continue;
                long length = files[j].length();
                if (files[j].delete()) freed += length;
            }
        }
        return freed;
    }

    /**
     * Deletes all cached videos from both storage locations.
     *
     * @return number of bytes freed
     */
    public static long clearAll(Context context) {
        File[] dirs = getCacheDirs(context);
        long freed = 0;
        for (int i = 0; i < dirs.length; i++) {
            File[] files = dirs[i].listFiles();
            if (files == null) continue;
            for (int j = 0; j < files.length; j++) {
                if (!files[j].isFile()) continue;
                long length = files[j].length();
                if (files[j].delete()) freed += length;
            }
            dirs[i].delete();
        }
        return freed;
    }

    private static File getPreferredDir(Context context) {
        if (isExternalAvailable()) {
            File external = getExternalDir();
            if (ensureDir(external)) return external;
        }
        File internal = getInternalDir(context);
        ensureDir(internal);
        return internal;
    }

    private static boolean ensureDir(File dir) {
        try {
            if (!dir.exists()) {
                if (!dir.mkdirs() && !dir.exists()) return false;
            }
            return dir.isDirectory() && dir.canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    private static long getAvailableBytes(File dir) {
        try {
            StatFs stat = new StatFs(dir.getPath());
            return (long) stat.getBlockSize() * (long) stat.getAvailableBlocks();
        } catch (Exception e) {
            return 0;
        }
    }

    private static long getDirectorySize(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        long total = 0;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) total += files[i].length();
        }
        return total;
    }

    private static String getContentUri(String path) {
        String name = new File(path).getName();
        if (name.endsWith(".mp4")) {
            name = name.substring(0, name.length() - 4);
        }
        return "content://" + VideoContentProvider.AUTHORITY + "/" + Uri.encode(name);
    }

    private static String getFileName(String videoId) {
        StringBuilder sb = new StringBuilder();
        if (videoId != null) {
            for (int i = 0; i < videoId.length(); i++) {
                char c = videoId.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                        (c >= '0' && c <= '9') || c == '-' || c == '_') {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            }
        }
        if (sb.length() == 0) sb.append("video");
        return sb.toString() + ".mp4";
    }
}
