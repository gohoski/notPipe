package io.github.gohoski.notpipe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by Gleb on 15.11.2026.
 * Serves cached videos from app-private internal storage through content://
 * URIs. The media framework and external players receive an open file
 * descriptor, so the cache does not need world-readable permissions.
 */
public class VideoContentProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.gohoski.notpipe.videos";
    private static final String MIME_TYPE = "video/mp4";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = VideoStorage.getInternalFile(getContext(), uri.getLastPathSegment());
        if (file == null || !file.exists() || !file.isFile()) {
            throw new FileNotFoundException(uri.toString());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return MIME_TYPE;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
