package com.yuyan.imemodule.application

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.yuyan.imemodule.manager.ClipboardHelper
import com.yuyan.imemodule.manager.DataBaseKT
import com.yuyan.imemodule.manager.ThemeManager
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.AssetUtils.copyFileOrDir
import com.yuyan.imemodule.utils.thread.ThreadPoolUtils
import com.yuyan.inputmethod.core.Kernel
import java.io.File

class Launcher {
    lateinit var context: Context
        private set

    fun initData(context: Context) {
        this.context = context
        currentInit()
        onInitDataChildThread()
    }

    private fun currentInit() {
        AppPrefs.init(PreferenceManager.getDefaultSharedPreferences(context))
        ThemeManager.init(context.resources.configuration)
        DataBaseKT.instance.sideSymbolDao().getAllSideSymbolPinyin()  //操作一次查询，提前创建数据库，避免使用时才创建
        ClipboardHelper.init()
    }

    /**
     * 可以在子线程初始化的操作
     */
    private fun onInitDataChildThread() {
        ThreadPoolUtils.executeSingleton {
            // 复制词库文件（仅首次或版本更新时）
            val dataDictVersion = AppPrefs.getInstance().internal.dataDictVersion.getValue()
            if (dataDictVersion < CustomConstant.CURRENT_RIME_DICT_DATA_VERSIOM) {
                // 确保目标目录存在
                val rimeDir = File(CustomConstant.RIME_DICT_PATH)
                val hwDir = File(CustomConstant.HW_DICT_PATH)
                
                // rime词库：仅当目录不存在或为空时才复制
                if (!rimeDir.exists() || (rimeDir.list()?.isEmpty() == true)) {
                    rimeDir.mkdirs()
                    copyFileOrDir(context, "rime", "", CustomConstant.RIME_DICT_PATH, false)
                }
                
                // hw词库：仅当目录不存在或为空时才复制
                if (!hwDir.exists() || (hwDir.list()?.isEmpty() == true)) {
                    hwDir.mkdirs()
                    copyFileOrDir(context, "hw", "", CustomConstant.HW_DICT_PATH, false)
                }
                
                AppPrefs.getInstance().internal.dataDictVersion.setValue(CustomConstant.CURRENT_RIME_DICT_DATA_VERSIOM)
            }
            Kernel.resetIme()  // 解决词库复制慢，导致先调用初始化问题
            YuyanEmojiCompat.init(context)
            //初始化键盘主题
            val isFollowSystemDayNight = prefs.followSystemDayNightTheme.getValue()
            if (isFollowSystemDayNight) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        val instance = Launcher()
    }
}
