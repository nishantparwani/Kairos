package com.kairos.campus

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
  // Same channel, icon and style as the app's local notifications.
  sendLocalNotification(this,message.notification?.title ?: "Kairos",message.notification?.body ?: "Your session has an update")
 }
}
