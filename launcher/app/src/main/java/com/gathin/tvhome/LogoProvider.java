package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * The company logo, sent by the TVFreedom phone app over adb:
 * <pre>
 * content write --uri content://com.gathin.tvhome.logo/logo &lt; logo.png
 * content delete --uri content://com.gathin.tvhome.logo/logo
 * </pre>
 * The manifest guards it with android.permission.DUMP, like {@link ConfigReceiver}. Home shows it the next time it
 * starts ({@link Branding}).
 */
public class LogoProvider extends ContentProvider {

    static final String FILE = "logo.png";

    static File file(android.content.Context ctx) {
        return new File(ctx.getFilesDir(), FILE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        File f = file(getContext());
        int flags = mode.contains("w")
                ? ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, flags);
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] args) {
        return file(getContext()).delete() ? 1 : 0;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return "image/png";
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
