package com.example.change2proimage

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class StickerQueueService:Service(){private lateinit var gs:GenerationTaskStore;private lateinit var ss:StickerStore
 override fun onCreate(){super.onCreate();gs=GenerationTaskStore(this);ss=StickerStore(this);if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("sticker_queue","表情包任务",NotificationManager.IMPORTANCE_LOW))}
 override fun onBind(i:Intent?):IBinder?=null
 override fun onStartCommand(i:Intent?,f:Int,startId:Int):Int{val id=i?.getStringExtra("batch_id").orEmpty();val b=ss.getBatch(id)?:return START_NOT_STICKY;startForeground(7202,NotificationCompat.Builder(this,"sticker_queue").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("酸奶喵 · 表情包").setContentText("正在生成 ${b.ocName} 的表情包").setOngoing(true).build());Thread{runBatch(b);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId)}.start();return START_NOT_STICKY}
 private fun runBatch(b:StickerQueueBatch){for(id in b.generationTaskIds){var t=gs.get(id)?:continue;if(t.status==GenerationTask.STATUS_SUCCESS)continue;if(t.status==GenerationTask.STATUS_FAILED)continue;while(gs.getAll().any{it.id!=id&&it.isRunning()&&it.status!=GenerationTask.STATUS_QUEUED})Thread.sleep(1200);ContextCompat.startForegroundService(this,Intent(this,GenerationService::class.java).putExtra(GenerationService.EXTRA_TASK_ID,id));while(true){Thread.sleep(1200);t=gs.get(id)?:break;if(t.isTerminal())break}}}
}
