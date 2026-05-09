/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025 WhiteFrost Suretype
 */
package org.fcitx.fcitx5.android.data

import android.content.Context
import timber.log.Timber
import java.io.File

/**
 * Deploys Suretype Rime schema files from APK assets to the Rime user data directory.
 *
 * The Rime plugin looks for user schemas in {filesDir}/rime/.
 * On first launch (or when schemas are missing), this copies the bundled
 * suretype_14key.schema.yaml and supporting files so the Rime engine can
 * discover and deploy them (build prism).
 */
object RimeDeployer {

    private const val ASSETS_DIR = "rime"
    private const val RIME_SUBDIR = "rime"

    /** Files that must exist in the Rime user dir for deployment to be considered complete. */
    private val requiredFiles = listOf(
        "suretype_14key.schema.yaml",
        "suretype_14key.dict.yaml",
        "default.custom.yaml"
    )

    /**
     * Ensure Rime schema files are deployed to the user data directory.
     * Idempotent — skips if all required files already exist.
     *
     * @param context application context
     */
    fun ensureDeployed(context: Context) {
        val rimeDir = File(context.filesDir, RIME_SUBDIR)
        if (requiredFiles.all { File(rimeDir, it).exists() }) {
            Timber.d("RimeDeployer: all schema files present, skipping")
            return
        }

        Timber.i("RimeDeployer: deploying schema files to $rimeDir")
        try {
            if (!rimeDir.exists()) {
                rimeDir.mkdirs()
            }
            val assets = context.assets.list(ASSETS_DIR) ?: run {
                Timber.w("RimeDeployer: no assets found in $ASSETS_DIR")
                return
            }
            for (filename in assets) {
                val target = File(rimeDir, filename)
                if (target.exists()) continue
                context.assets.open("$ASSETS_DIR/$filename").use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Timber.d("RimeDeployer: copied $filename")
            }
            Timber.i("RimeDeployer: deployment complete (${assets.size} files)")
        } catch (e: Exception) {
            Timber.e(e, "RimeDeployer: deployment failed")
        }
    }
}
