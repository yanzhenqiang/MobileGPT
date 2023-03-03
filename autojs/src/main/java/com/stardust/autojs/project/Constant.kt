package com.stardust.autojs.project

/**
 * @author wilinz
 * @date 2022/5/23
 */
object Constant {
    object Libraries {
        val OPEN_CV = listOf("libopencv_java4.so")
        val GOOGLE_ML_KIT_OCR = listOf("libmlkit_google_ocr_pipeline.so")
        val TERMINAL_EMULATOR = listOf(
            "libjackpal-androidterm5.so",
            "libjackpal-termexec2.so"
        )
    }

    object Assets {
        const val PROJECT = "/project"
        const val GOOGLE_ML_KIT_OCR = "/mlkit-google-ocr-models"
    }

    object Permissions {
        const val ACCESSIBILITY_SERVICES = "accessibility_services"
        const val BACKGROUND_START = "background_start"
        const val DRAW_OVERLAY = "draw_overlay"
    }

    object Protocol {
        const val ASSETS = "file:///android_asset"
    }

    object Abi {
        const val ARM64_V8A = "arm64-v8a"
        const val X86_64 = "x86_64"
        val abis = listOf(ARM64_V8A, X86_64)
    }

    object ResourceId {
        const val LAUNCHER_ICON = "ic_launcher"
        const val SPLASH_ICON = "autojs_logo"
    }
}