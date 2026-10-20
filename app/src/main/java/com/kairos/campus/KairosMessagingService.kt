package com.kairos.campus

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class KairosMessagingService: FirebaseMessagingService() {
 override fun onNewToken(token:String) {
  if(BuildConfig.DEMO) return
  FirebaseAuth.getInstance().currentUser?.let { if(it.isEmailVerified) FirebaseFirestore.getInstance().collection("tokens").document(it.uid).set(mapOf("token" to token)) }
 }
 override fun onMessageReceived(message:RemoteMessage) {
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("sessions","Kairos sessions",NotificationManager.IMPORTANCE_DEFAULT))
  val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  manager.notify((System.currentTimeMillis()%100000).toInt(),android.app.Notification.Builder(this,"sessions").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(message.notification?.title ?: "Kairos").setContentText(message.notification?.body ?: "Your session has an update").setContentIntent(intent).setAutoCancel(true).build())
 }
}
