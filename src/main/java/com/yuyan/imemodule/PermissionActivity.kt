package com.yuyan.imemodule

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yuyan.imemodule.databinding.ActivityPermissionBinding
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.thread.ThreadPoolUtils
import java.io.File

/**
 * 隐私友好的存储权限引导页面
 * 使用 SAF (Storage Access Framework) 替代 MANAGE_EXTERNAL_STORAGE
 */
class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding

    // 用户必须同意隐私政策才能继续
    private val privacyAccepted: Boolean
        get() = AppPrefs.getInstance().internal.privacyPolicySure.getValue()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            windowInsets
        }
        setContentView(binding.root)

        // 检查隐私政策
        if (!privacyAccepted) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setupViews()
    }

    private fun setupViews() {
        binding.apply {
            // 隐私友好的说明
            privacyDescription.text = getString(
                com.yuyan.imemodule.R.string.storage_permission_description,
                getString(com.yuyan.imemodule.R.string.app_name)
            )

            // 跳过按钮 - 高级用户可选择不使用外部存储
            skipButton.setOnClickListener {
                // 标记用户选择跳过，后续词库使用内置默认
                AppPrefs.getInstance().internal.storagePermissionGranted.setValue(false)
                AppPrefs.getInstance().internal.storageSkipChosen.setValue(true)
                startMainActivity()
            }

            // 授予权限按钮
            grantPermissionButton.setOnClickListener {
                requestStoragePermission()
            }
        }

        updateUI()
    }

    private fun updateUI() {
        val hasPermission = checkStoragePermission()
        binding.apply {
            grantPermissionButton.isEnabled = !hasPermission
            grantPermissionButton.text = if (hasPermission) {
                getString(com.yuyan.imemodule.R.string.storage_permission_granted)
            } else {
                getString(com.yuyan.imemodule.R.string.grant_storage_permission)
            }

            // 如果已有权限，直接跳转到主界面
            if (hasPermission) {
                ThreadPoolUtils.executeSingleton {
                    // 确保目录存在
                    ensureDirectoriesExist()
                    runOnUiThread { startMainActivity() }
                }
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Android 10 及以下使用传统权限
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == 0
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 MANAGE_EXTERNAL_STORAGE
            // 但我们提供"选择文件目录"选项，让用户主动授权
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            } catch (e: Exception) {
                // 部分厂商不支持，尝试直接请求
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            }
        } else {
            // Android 10 及以下
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_LEGACY_STORAGE
            )
        }
    }

    private fun ensureDirectoriesExist() {
        // 创建必要的目录
        listOf(
            File(getExternalFilesDir(null), "rime"),
            File(getExternalFilesDir(null), "hw")
        ).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                AppPrefs.getInstance().internal.storagePermissionGranted.setValue(true)
                ThreadPoolUtils.executeSingleton {
                    ensureDirectoriesExist()
                    runOnUiThread { startMainActivity() }
                }
            } else {
                // 用户拒绝或部分厂商返回，但可能实际有权限
                updateUI()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LEGACY_STORAGE) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == 0 }
            AppPrefs.getInstance().internal.storagePermissionGranted.setValue(granted)
            if (granted) {
                ThreadPoolUtils.executeSingleton {
                    ensureDirectoriesExist()
                    runOnUiThread { startMainActivity() }
                }
            } else {
                // 继续，但告知用户词库功能受限
                AppPrefs.getInstance().internal.storagePermissionGranted.setValue(false)
                startMainActivity()
            }
        }
    }

    companion object {
        private const val REQUEST_MANAGE_STORAGE = 1001
        private const val REQUEST_LEGACY_STORAGE = 1002
    }
}
