package ru.dogmen.lexrf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class UpdateApkProvider extends ContentProvider {
    static final Uri APK = Uri.parse("content://ru.dogmen.lexrf.update/LexRF.apk");
    static final String MIME = "application/vnd.android.package-archive";
    static final String FILE = "LexRF-update.apk";

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return APK.equals(uri) ? MIME : null; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!APK.equals(uri) || !"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), FILE), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String sort) {
        if (!APK.equals(uri)) return null;
        if (columns == null) columns = new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add("LexRF.apk");
            else if (OpenableColumns.SIZE.equals(column)) row.add(new File(getContext().getCacheDir(), FILE).length());
            else row.add(null);
        }
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
