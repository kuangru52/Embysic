package com.kuangru52.embysic

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageHelper {
    fun getLocalizedContext(context: Context, langCode: String): Context {
        val systemLocale = try {
            context.resources.configuration.locales[0]
        } catch (_: Exception) {
            Locale.getDefault()
        }

        val targetLocale = when (langCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> {
                val sysLang = systemLocale?.language?.lowercase(Locale.ROOT) ?: "zh"
                if (sysLang.startsWith("zh")) {
                    Locale.SIMPLIFIED_CHINESE
                } else {
                    Locale.ENGLISH
                }
            }
        }

        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        return context.createConfigurationContext(config)
    }
}
