package dev.powerfulcalendar.ui.v2

import dev.powerfulcalendar.core.PowerStore
import org.junit.Assert.*
import org.junit.Test

class RestoreFailureExplanationTest {
    @Test fun nonEmptyTargetExplainsThatRestoreWasRefused() {
        val text = RestoreFailureExplanation.explain(PowerStore.NonEmptyRestoreTargetException())
        assertTrue(text.contains("不是空库"))
        assertTrue(text.contains("已拒绝"))
        assertTrue(text.contains("先导出"))
        assertFalse(text.contains("IllegalArgumentException"))
    }

    @Test fun wrappedFailureStillExplainsNonEmptyTarget() {
        val cause = PowerStore.NonEmptyRestoreTargetException()
        val wrapped = RuntimeException("job failed", IllegalStateException("middle", cause))
        assertTrue(RestoreFailureExplanation.explain(wrapped).contains("不是空库"))
    }

    @Test fun invalidJsonAndHashFailureGiveUsefulSafeMessages() {
        val invalid = RestoreFailureExplanation.explain(
            IllegalArgumentException("备份文件不是有效 JSON"))
        assertTrue(invalid.contains("JSON"))
        val tampered = RestoreFailureExplanation.explain(
            IllegalArgumentException("payload SHA-256 mismatch"))
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
