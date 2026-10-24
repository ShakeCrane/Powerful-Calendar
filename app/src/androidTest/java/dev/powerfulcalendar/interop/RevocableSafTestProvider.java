package dev.powerfulcalendar.interop;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Synthetic provider fixture used only to exercise Android's URI-grant enforcement. */
public final class RevocableSafTestProvider extends ContentProvider {
    public static final String TARGET_PACKAGE = "dev.powerfulcalendar";
    public static final Uri SYNTHETIC_URI = Uri.parse(
            "content://dev.powerfulcalendar.test.revocable-saf/backup/probe");
    public static final String SYNTHETIC_BACKUP = "synthetic backup content";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Bundle call(String authority, String method, String targetPackage, Bundle extras) {
        if (!TARGET_PACKAGE.equals(targetPackage)) {
            throw new SecurityException("unexpected target package");
        }
        Context providerContext = getContext();
        if (providerContext == null) throw new IllegalStateException("provider is detached");
        switch (method) {
            case "grantRead":
                providerContext.grantUriPermission(
                        targetPackage, SYNTHETIC_URI,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                break;
            case "revokeRead":
                providerContext.revokeUriPermission(
                        targetPackage, SYNTHETIC_URI,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                break;
            default:
                throw new IllegalArgumentException("unsupported test-provider call");
        }
        return Bundle.EMPTY;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only test provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only test provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only test provider");
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(SYNTHETIC_BACKUP.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // A revoked/closed reader is the expected lifetime boundary for this fixture.
                }
            }, "revocable-saf-fixture-writer");
            writer.start();
            return pipe[0];
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }
}
