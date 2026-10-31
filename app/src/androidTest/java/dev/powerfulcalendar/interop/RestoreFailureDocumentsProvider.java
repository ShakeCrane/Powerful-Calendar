package dev.powerfulcalendar.interop;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;
import java.io.IOException;

/** Test-only DocumentsUI root whose selected document fails when PowerCal reads it. */
public final class RestoreFailureDocumentsProvider extends DocumentsProvider {
    public static final String ROOT_ID = "powercal-restore-qa";
    public static final String ROOT_DOCUMENT_ID = "root";
    public static final String BACKUP_DOCUMENT_ID = "permission-probe";
    public static final String ROOT_TITLE = "PowerCal QA Restore Probe";
    public static final String BACKUP_NAME = "SEP11 permission probe.json";

    private static volatile boolean denyRead = true;

    private static String[] rootColumns(String[] projection) {
        return projection != null ? projection : new String[] {
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                DocumentsContract.Root.COLUMN_MIME_TYPES
        };
    }

    private static String[] documentColumns(String[] projection) {
        return projection != null ? projection : new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(rootColumns(projection));
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            if (DocumentsContract.Root.COLUMN_ROOT_ID.equals(column)) row.add(ROOT_ID);
            else if (DocumentsContract.Root.COLUMN_FLAGS.equals(column)) {
                row.add(DocumentsContract.Root.FLAG_LOCAL_ONLY |
                        DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
            } else if (DocumentsContract.Root.COLUMN_TITLE.equals(column)) row.add(ROOT_TITLE);
            else if (DocumentsContract.Root.COLUMN_DOCUMENT_ID.equals(column)) {
                row.add(ROOT_DOCUMENT_ID);
            } else if (DocumentsContract.Root.COLUMN_MIME_TYPES.equals(column)) {
                row.add("application/json");
            } else if (DocumentsContract.Root.COLUMN_ICON.equals(column)) row.add(0);
            else if (DocumentsContract.Root.COLUMN_AVAILABLE_BYTES.equals(column)) row.add(0L);
            else row.add(null);
        }
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        if (!ROOT_DOCUMENT_ID.equals(documentId) && !BACKUP_DOCUMENT_ID.equals(documentId)) {
            throw new FileNotFoundException("unknown synthetic restore document");
        }
        MatrixCursor cursor = new MatrixCursor(documentColumns(projection));
        MatrixCursor.RowBuilder row = cursor.newRow();
        boolean isRoot = ROOT_DOCUMENT_ID.equals(documentId);
        for (String column : cursor.getColumnNames()) {
            if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)) row.add(documentId);
            else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column)) {
                row.add(isRoot ? ROOT_TITLE : BACKUP_NAME);
            } else if (DocumentsContract.Document.COLUMN_MIME_TYPE.equals(column)) {
                row.add(isRoot ? DocumentsContract.Document.MIME_TYPE_DIR : "application/json");
            } else if (DocumentsContract.Document.COLUMN_FLAGS.equals(column)) row.add(0);
            else if (DocumentsContract.Document.COLUMN_SIZE.equals(column)) row.add(isRoot ? 0L : 128L);
            else if (DocumentsContract.Document.COLUMN_LAST_MODIFIED.equals(column)) row.add(0L);
            else if (DocumentsContract.Document.COLUMN_ICON.equals(column)) row.add(0);
            else row.add(null);
        }
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                      String sortOrder) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(documentColumns(projection));
        if (!ROOT_DOCUMENT_ID.equals(parentDocumentId)) return cursor;
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)) {
                row.add(BACKUP_DOCUMENT_ID);
            } else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column)) {
                row.add(BACKUP_NAME);
            } else if (DocumentsContract.Document.COLUMN_MIME_TYPE.equals(column)) {
                row.add("application/json");
            } else if (DocumentsContract.Document.COLUMN_FLAGS.equals(column)) row.add(0);
            else if (DocumentsContract.Document.COLUMN_SIZE.equals(column)) row.add(128L);
            else if (DocumentsContract.Document.COLUMN_LAST_MODIFIED.equals(column)) row.add(0L);
            else if (DocumentsContract.Document.COLUMN_ICON.equals(column)) row.add(0);
            else row.add(null);
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
                                            CancellationSignal signal)
            throws FileNotFoundException {
        if (!BACKUP_DOCUMENT_ID.equals(documentId) || !"r".equals(mode)) {
            throw new FileNotFoundException("synthetic restore document is unavailable");
        }
        if (denyRead) throw new SecurityException("synthetic SAF provider denied backup read");
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            pipe[1].close();
            return pipe[0];
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override
    public String createDocument(String documentId, String mimeType, String displayName)
            throws FileNotFoundException {
        throw new FileNotFoundException("read-only QA provider");
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        throw new FileNotFoundException("read-only QA provider");
    }

    @Override
    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        throw new FileNotFoundException("read-only QA provider");
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return ROOT_DOCUMENT_ID.equals(parentDocumentId) &&
                BACKUP_DOCUMENT_ID.equals(documentId);
    }
}
