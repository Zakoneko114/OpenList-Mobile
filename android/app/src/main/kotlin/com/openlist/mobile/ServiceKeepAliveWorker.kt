package com.dykt.openlist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dykt.openlist.config.AppConfig
import java.util.concurrent.TimeUnit

/**
 * 服务保活 Worker - 定期检查并重启 OpenList 服务
 * 用于 Android 13+ 系统，确保服务在后台持续运行
 */
class ServiceKeepAliveWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val TAG = "ServiceKeepAliveWorker"
        private const val WORK_NAME = "openlist_service_keepalive"
        
        /**
         * 启动定期保活任务
         */
        fun startKeepAliveWork(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
                
                // 每 15 分钟检查一次服务状态 (最短周期)
                val workRequest = PeriodicWorkRequestBuilder<ServiceKeepAliveWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .addTag(WORK_NAME)
                    .build()
                
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                
                Log.d(TAG, "Keep-alive work scheduled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule keep-alive work", e)
            }
        }
        
        /**
         * 停止保活任务
         */
        fun stopKeepAliveWork(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Log.d(TAG, "Keep-alive work cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel keep-alive work", e)
            }
        }
    }
    
    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Checking service status in keep-alive worker")
            
            // 如果用户没有手动停止服务，则检查并重启
            if (!AppConfig.isManuallyStoppedByUser) {
                if (!OpenListService.isRunning) {
                    Log.d(TAG, "Service not running, attempting restart from worker")
                    
                    val intent = Intent(applicationContext, OpenListService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(intent)
                    } else {
                        applicationContext.startService(intent)
                    }
                    
                    // 等待一段时间后再次检查
                    Thread.sleep(3000)
                    
                    if (OpenListService.isRunning) {
                        Log.d(TAG, "Service restarted successfully")
                        Result.success()
                    } else {
                        Log.w(TAG, "Service restart may have failed, will retry next cycle")
                        Result.retry()
                    }
                } else {
                    Log.d(TAG, "Service is running, no action needed")
                    Result.success()
                }
            } else {
                Log.d(TAG, "Service was manually stopped by user, skipping restart")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in keep-alive worker", e)
            Result.retry()
        }
    }
}
