package com.yuyan.imemodule.ui.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.yuyan.imemodule.R
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.InputMethodUtil

enum class SetupPage {
    Privacy,        // 隐私政策确认
    Storage,        // 存储权限（可选）
    Enable,         // 启用输入法
    Select;         // 选择输入法

    fun getHintText(context: Context) = context.getString(
        when (this) {
            Privacy -> R.string.privacy_policy_hint
            Storage -> R.string.storage_permission_setup_hint
            Enable -> R.string.enable_ime_hint
            Select -> R.string.select_ime_hint
        }, context.getString(R.string.app_name)
    ).let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) }

    fun getButtonText(context: Context) = context.getText(
        when (this) {
            Privacy -> R.string.agree_and_continue
            Storage -> R.string.grant_storage_permission
            Enable -> R.string.enable_ime
            Select -> R.string.select_ime
        }
    )

    fun getButtonAction(context: Context) = when (this) {
        Privacy -> {
            // 标记隐私政策已同意
            AppPrefs.getInstance().internal.privacyPolicySure.setValue(true)
        }
        Storage -> {
            // 打开存储权限设置页面
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            } else {
                ContextCompat.startActivity(
                    context,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                    null
                )
            }
        }
        Enable -> InputMethodUtil.startSettingsActivity(context)
        Select -> InputMethodUtil.showPicker()
    }

    fun isDone() = when (this) {
        Privacy -> AppPrefs.getInstance().internal.privacyPolicySure.getValue()
        Storage -> AppPrefs.getInstance().internal.storageSkipChosen.getValue() || checkStoragePermission()
        Enable -> InputMethodUtil.isEnabled()
        Select -> InputMethodUtil.isSelected()
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Android 10 及以下，检查传统存储权限
            AppPrefs.getInstance().internal.storagePermissionGranted.getValue()
        }
    }

    companion object {
        fun valueOf(value: Int) = entries[value]
        fun SetupPage.isLastPage() = this == entries.last()
        fun Int.isLastPage() = this == entries.size - 1
        fun hasUndonePage() = entries.any { !it.isDone() }
        fun firstUndonePage() = entries.firstOrNull { !it.isDone() }
    }
}
