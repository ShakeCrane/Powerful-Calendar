package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.api.v1.BackupRestoreExceptionV1
import dev.powerfulcalendar.core.api.v1.BackupRestoreFailureKindV1
import org.junit.Assert.*
import org.junit.Test

class RestoreFailureExplanationTest {
    @Test fun nonEmptyTargetExplainsThatRestoreWasRefused() {
        val text = RestoreFailureExplanation.explain(
            BackupRestoreExceptionV1(BackupRestoreFailureKindV1.TARGET_NOT_EMPTY))
        assertTrue(text.contains("不是空库"))
        assertTrue(text.contains("已拒绝"))
        assertTrue(text.contains("先导出"))
        assertFalse(text.contains("IllegalArgumentException"))
    }

    @Test fun wrappedFailureStillExplainsNonEmptyTarget() {
        val cause = BackupRestoreExceptionV1(BackupRestoreFailureKindV1.TARGET_NOT_EMPTY)
        val wrapped = RuntimeException("job failed", IllegalStateException("middle", cause))
        assertTrue(RestoreFailureExplanation.explain(wrapped).contains("不是空库"))
    }

    @Test fun invalidJsonAndHashFailureGiveUsefulSafeMessages() {
        val invalid = RestoreFailureExplanation.explain(
            BackupRestoreExceptionV1(BackupRestoreFailureKindV1.INVALID_JSON))
        assertTrue(invalid.contains("JSON"))
        val tampered = RestoreFailureExplanation.explain(
            BackupRestoreExceptionV1(BackupRestoreFailureKindV1.INTEGRITY_FAILURE))
        assertTrue(tampered.contains("校验失败"))
    }

    @Test fun unauthorizedFileUseReturnsPermissionGuide() {
        val text = RestoreFailureExplanation.explain(SecurityException("private token"))
        assertTrue(text.contains("权限"))
        assertFalse(text.contains("private token"))
    }

    @Test fun unknownErrorNeverLeaksExceptionOrSensitiveDetails() {
        val text = RestoreFailureExplanation.explain(
            IllegalArgumentException("Authorization: Bearer sensitive-example"))
        assertTrue(text.contains("无法安全恢复"))
        assertFalse(text.contains("sensitive-example"))
    }
}
