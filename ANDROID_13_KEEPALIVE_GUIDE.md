# Android 13+ 完全保活指南

## 已实现的保活机制

本应用已实现多层保活机制，确保在 Android 13+ 系统上即使关闭窗口和熄屏也能持续后台运行：

### 1. 前台服务 (Foreground Service)
- 显示持久通知，提升服务优先级
- 使用 `FOREGROUND_SERVICE_DATA_SYNC` 类型
- 设置 `FLAG_NO_CLEAR` 和 `FLAG_ONGOING_EVENT` 防止被清除

### 2. 唤醒锁 (Wake Lock)
- 获取 `PARTIAL_WAKE_LOCK` 保持 CPU 运行
- 即使屏幕关闭也能继续工作
- 可通过设置开关控制

### 3. START_STICKY 标志
- 服务被系统杀死后自动重启
- 不立即调用 `stopSelf()` 让系统自行管理重启

### 4. WorkManager 定期保活
- 每 15 分钟检查一次服务状态
- 如果服务未运行且用户未手动停止，则自动重启
- 使用系统推荐的任务调度方式

### 5. stopWithTask=false
- 用户滑掉应用后服务继续运行
- 不随任务一起被销毁

### 6. 开机自启动
- 监听 `BOOT_COMPLETED` 广播
- 系统启动后自动启动服务

## 用户需要进行的设置

由于 Android 13+ 系统对后台限制的加强，用户需要手动进行以下设置：

### 1. 忽略电池优化（必须）
```
设置 → 应用 → OpenList → 电池 → 无限制
```
或在应用中点击"请求忽略电池优化"按钮

### 2. 允许自启动（国产 ROM 必须）
不同品牌手机路径不同：
- **小米**: 手机管家 → 授权管理 → 自启动管理
- **华为**: 手机管家 → 应用启动管理 → 关闭自动管理，手动开启
- **OPPO**: 手机管家 → 权限隐私 → 自启动管理
- **Vivo**: i 管家 → 应用管理 → 权限管理 → 自启动
- **魅族**: 安全中心 → 应用管理 → 权限管理 → 后台管理
- **三星**: 设置 → 应用程序 → OpenList → 电池

### 3. 锁定应用（推荐）
在多任务界面下拉或点击锁定图标，防止被一键清理

### 4. 允许后台活动（必须）
```
设置 → 应用 → OpenList → 电池 → 允许后台活动
```

### 5. 关闭省电模式（推荐）
省电模式会严格限制后台活动，建议关闭或添加白名单

## 开发者配置说明

### AndroidManifest.xml 配置
```xml
<!-- 前台服务权限 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- 唤醒锁权限 -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- 电池优化白名单请求 -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- 开机启动权限 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- 服务配置 -->
<service
    android:name=".OpenListService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync"
    android:stopWithTask="false" />
```

### 依赖配置 (build.gradle)
```gradle
dependencies {
    // WorkManager for background tasks
    implementation 'androidx.work:work-runtime-ktx:2.9.0'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 关键代码实现

#### 1. 服务保活 Worker
```kotlin
class ServiceKeepAliveWorker : Worker() {
    override fun doWork(): Result {
        if (!AppConfig.isManuallyStoppedByUser && !OpenListService.isRunning) {
            // 重启服务
            val intent = Intent(applicationContext, OpenListService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        }
        return Result.success()
    }
}
```

#### 2. 唤醒锁管理
```kotlin
@SuppressLint("WakelockTimeout")
private fun ensureWakeLock() {
    if (AppConfig.isWakeLockEnabled && mWakeLock == null) {
        mWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "openlist::service"
        )
        mWakeLock?.acquire()
    }
}
```

#### 3. 前台服务启动
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // 启动前台服务
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        initOrUpdateNotification()
        startForeground(FOREGROUND_ID, notification)
    }
    
    // 返回 START_STICKY 确保服务被杀后重启
    return START_STICKY
}
```

## 测试验证

### 验证步骤：
1. 启动应用并开启服务
2. 按 Home 键回到桌面
3. 关闭屏幕等待 5 分钟
4. 亮屏检查通知栏是否有服务通知
5. 查看日志确认服务仍在运行

### 日志关键字：
```
OpenListService: Service is running
OpenListService: Wake lock acquired
ServiceKeepAliveWorker: Checking service status
ServiceKeepAliveWorker: Service is running, no action needed
```

## 故障排查

### 服务仍然被杀死？
1. 检查是否已忽略电池优化
2. 检查是否开启了自启动权限
3. 检查是否在省电模式下
4. 查看系统日志确认被杀原因

### WorkManager 不执行？
1. 检查是否限制了后台数据
2. 检查是否禁用了自动同步
3. 尝试重启设备

### 通知不显示？
1. 检查通知权限是否授予
2. 检查通知渠道是否正确创建
3. 检查是否误删了通知渠道

## 注意事项

⚠️ **重要提示**：
- 即使实现了所有保活机制，某些激进的系统（如 MIUI、ColorOS）仍可能杀死服务
- 必须引导用户完成所有必要的设置
- 建议在首次启动时显示设置引导页面
- 定期检查服务状态并在发现异常时提醒用户

## 参考资源

- [Android 13 后台执行限制](https://developer.android.com/about/versions/13/changes/fgs-types-required)
- [WorkManager 官方文档](https://developer.android.com/topic/libraries/architecture/workmanager)
- [前台服务最佳实践](https://developer.android.com/guide/components/foreground-services)
