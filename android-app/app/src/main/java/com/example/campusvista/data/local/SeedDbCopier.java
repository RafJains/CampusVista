package com.example.campusvista.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SeedDbCopier {
    private static final String[] REQUIRED_NON_EMPTY_TABLES = {
            "checkpoints",
            "places",
            "edges",
            "crowd_rules",
            "outdoor_panos",
            "recognition_refs",
            "search_aliases"
    };

    private SeedDbCopier() {
    }

    public static synchronized void copyDatabaseIfNeeded(Context context) {
        Context appContext = context.getApplicationContext();
        File dbFile = appContext.getDatabasePath(DBConfig.DB_NAME);
        SharedPreferences preferences = appContext.getSharedPreferences(
                DBConfig.PREFS_NAME,
                Context.MODE_PRIVATE
        );

        int copiedVersion = preferences.getInt(DBConfig.PREF_COPIED_DB_VERSION, 0);
        if (copiedVersion == DBConfig.DB_VERSION && isCopiedDatabaseValid(dbFile)) {
            return;
        }

        try {
            copyFreshDatabase(appContext, dbFile);
            preferences.edit()
                    .putInt(DBConfig.PREF_COPIED_DB_VERSION, DBConfig.DB_VERSION)
                    .apply();
        } catch (IOException exception) {
            preferences.edit().remove(DBConfig.PREF_COPIED_DB_VERSION).apply();
            throw new IllegalStateException("Unable to prepare CampusVista seed database", exception);
        }
    }

    private static void copyFreshDatabase(Context context, File dbFile) throws IOException {
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create database directory: " + parent);
        }

        File tempFile = new File(dbFile.getAbsolutePath() + ".tmp");
        deleteIfExists(tempFile);

        CopyResult copyResult;
        try {
            copyResult = copyAssetToFile(context, tempFile);
            if (copyResult.bytesCopied <= 0 || tempFile.length() != copyResult.bytesCopied) {
                throw new IOException("Seed database file-size verification failed");
            }

            String copiedChecksum = sha256(tempFile);
            if (!copyResult.sha256.equals(copiedChecksum)) {
                throw new IOException("Seed database checksum verification failed");
            }

            if (!isCopiedDatabaseValid(tempFile)) {
                throw new IOException("Seed database SQLite integrity check failed");
            }

            deleteIfExists(dbFile);
            if (!tempFile.renameTo(dbFile)) {
                throw new IOException("Unable to move seed database into place");
            }
        } finally {
            deleteIfExists(tempFile);
        }
    }

    private static CopyResult copyAssetToFile(Context context, File destination)
            throws IOException {
        MessageDigest digest = newSha256Digest();
        long bytesCopied = 0L;
        byte[] buffer = new byte[DBConfig.COPY_BUFFER_SIZE_BYTES];

        try (InputStream inputStream = context.getAssets().open(DBConfig.SEED_DB_ASSET_PATH);
             FileOutputStream outputStream = new FileOutputStream(destination)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                bytesCopied += read;
            }
            outputStream.getFD().sync();
        }

        return new CopyResult(bytesCopied, toHex(digest.digest()));
    }

    static boolean isCopiedDatabaseValid(File dbFile) {
        if (dbFile == null || !dbFile.exists() || dbFile.length() <= 0) {
            return false;
        }

        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );
            cursor = database.rawQuery("PRAGMA integrity_check", null);
            if (!cursor.moveToFirst() || !"ok".equalsIgnoreCase(cursor.getString(0))) {
                return false;
            }
            return hasRequiredSeedContent(database);
        } catch (SQLiteException exception) {
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (database != null) {
                database.close();
            }
        }
    }

    private static boolean hasRequiredSeedContent(SQLiteDatabase database) {
        for (String table : REQUIRED_NON_EMPTY_TABLES) {
            Cursor cursor = null;
            try {
                cursor = database.rawQuery("SELECT COUNT(*) FROM " + table, null);
                if (!cursor.moveToFirst() || cursor.getLong(0) <= 0) {
                    return false;
                }
            } catch (SQLiteException exception) {
                return false;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return true;
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[DBConfig.COPY_BUFFER_SIZE_BYTES];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Unable to delete file: " + file);
        }
    }

    private static final class CopyResult {
        private final long bytesCopied;
        private final String sha256;

        private CopyResult(long bytesCopied, String sha256) {
            this.bytesCopied = bytesCopied;
            this.sha256 = sha256;
        }
    }
}
