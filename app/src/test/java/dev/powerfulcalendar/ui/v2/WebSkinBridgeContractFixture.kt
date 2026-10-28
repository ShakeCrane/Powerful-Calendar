package dev.powerfulcalendar.ui.v2

import org.json.JSONObject
import java.io.File

/** Loads the shared machine-readable contract from the repository during JVM tests. */
internal object WebSkinBridgeContractFixture {
    fun load(): JSONObject {
        val workingDirectory = System.getProperty("user.dir")
            ?: error("JVM did not provide user.dir")
        var directory: File? = File(workingDirectory).absoluteFile
        while (directory != null) {
            val candidate = File(directory, "docs/contracts/web-skin-bridge-v1.schema.json")
            if (candidate.isFile) return JSONObject(candidate.readText(Charsets.UTF_8))
            directory = directory.parentFile
        }
        error("Cannot locate docs/contracts/web-skin-bridge-v1.schema.json from user.dir")
    }
}
