package dev.powerfulcalendar.interop;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Test-only DocumentsUI root for deterministic restore-file failure cases. */
public final class RestoreFailureDocumentsProvider extends DocumentsProvider {
    public static final String ROOT_ID = "powercal-restore-qa";
    public static final String ROOT_DOCUMENT_ID = "root";
    public static final String ROOT_TITLE = "PowerCal QA Restore Probe";
    public static final String BACKUP_DOCUMENT_ID = "permission-probe";
    public static final String BACKUP_NAME = "SEP11 permission probe.json";
    public static final String MALFORMED_DOCUMENT_ID = "malformed-json";
    public static final String MALFORMED_BACKUP_NAME = "SEP11 malformed backup.json";
    public static final String FUTURE_SCHEMA_DOCUMENT_ID = "future-schema";
    public static final String FUTURE_SCHEMA_BACKUP_NAME = "SEP11 future schema backup.json";

    private static final byte[] MALFORMED_JSON =
            "{not a PowerCal backup".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FUTURE_SCHEMA_JSON = (
            "{\"format\":\"powercal-native\",\"backupFormatVersion\":1," +
                    "\"databaseSchemaVersion\":999}"
    ).getBytes(StandardCharsets.UTF_8);

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
            } else if (DocumentsContract.Root.COLUMN_TITLE.equals(column)) row.add(
                    "PowerCal QA Restore Probe");
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
        if (!ROOT_DOCUMENT_ID.equals(documentId) && !isProbeDocument(documentId)) {
            throw new FileNotFoundException("unknown synthetic restore document");
        }
        MatrixCursor cursor = new MatrixCursor(documentColumns(projection));
        addDocumentRow(cursor, documentId);
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                      String sortOrder) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(documentColumns(projection));
        if (!ROOT_DOCUMENT_ID.equals(parentDocumentId)) return cursor;
        addDocumentRow(cursor, BACKUP_DOCUMENT_ID);
        addDocumentRow(cursor, MALFORMED_DOCUMENT_ID);
        addDocumentRow(cursor, FUTURE_SCHEMA_DOCUMENT_ID);
        return cursor;
    }

    private static void addDocumentRow(MatrixCursor cursor, String documentId) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        boolean isRoot = ROOT_DOCUMENT_ID.equals(documentId);
        for (String column : cursor.getColumnNames()) {
            if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)) row.add(documentId);
            else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column)) {
                row.add(isRoot ? ROOT_TITLE : displayName(documentId));
            } else if (DocumentsContract.Document.COLUMN_MIME_TYPE.equals(column)) {
                row.add(isRoot ? DocumentsContract.Document.MIME_TYPE_DIR : "application/json");
            } else if (DocumentsContract.Document.COLUMN_FLAGS.equals(column)) row.add(0);
            else if (DocumentsContract.Document.COLUMN_SIZE.equals(column)) {
                row.add(isRoot ? 0L : documentSize(documentId));
            } else if (DocumentsContract.Document.COLUMN_LAST_MODIFIED.equals(column)) row.add(0L);
            else if (DocumentsContract.Document.COLUMN_ICON.equals(column)) row.add(0);
            else row.add(null);
        }
    }

    private static boolean isProbeDocument(String documentId) {
        return BACKUP_DOCUMENT_ID.equals(documentId) ||
                MALFORMED_DOCUMENT_ID.equals(documentId) ||
                FUTURE_SCHEMA_DOCUMENT_ID.equals(documentId);
    }

    private static String displayName(String documentId) {
        if (BACKUP_DOCUMENT_ID.equals(documentId)) return BACKUP_NAME;
        if (MALFORMED_DOCUMENT_ID.equals(documentId)) return MALFORMED_BACKUP_NAME;
        if (FUTURE_SCHEMA_DOCUMENT_ID.equals(documentId)) return FUTURE_SCHEMA_BACKUP_NAME;
        return "Unknown QA document";
    }

    private static long documentSize(String documentId) {
        if (MALFORMED_DOCUMENT_ID.equals(documentId)) return MALFORMED_JSON.length;
        if (FUTURE_SCHEMA_DOCUMENT_ID.equals(documentId)) return FUTURE_SCHEMA_JSON.length;
        return 128L;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
                                            CancellationSignal signal)
            throws FileNotFoundException {
        if (!"r".equals(mode) || !isProbeDocument(documentId)) {
            throw new FileNotFoundException("synthetic restore document is unavailable");
        }
        if (BACKUP_DOCUMENT_ID.equals(documentId)) {
            throw new SecurityException("synthetic SAF provider denied backup read");
        }
        return openBytes(MALFORMED_DOCUMENT_ID.equals(documentId)
                ? MALFORMED_JSON : FUTURE_SCHEMA_JSON);
    }

    private static ParcelFileDescriptor openBytes(byte[] bytes) throws FileNotFoundException {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(bytes);
                } catch (IOException ignored) {
                    // DocumentsUI may close the read side while the test is tearing down.
                }
            }, "powercal-restore-qa-writer");
            writer.setDaemon(true);
            writer.start();
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
        return ROOT_DOCUMENT_ID.equals(parentDocumentId) && isProbeDocument(documentId);
    }
}
