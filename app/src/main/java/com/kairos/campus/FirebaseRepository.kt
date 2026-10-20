package com.kairos.campus

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository: Repository {
 private val auth=FirebaseAuth.getInstance()
 private val db=FirebaseFirestore.getInstance()
 private val functions=FirebaseFunctions.getInstance("asia-south1")
 private val flow=MutableStateFlow(AppData())
 override val state: StateFlow<AppData> = flow
 private val listeners=mutableListOf<ListenerRegistration>()
 private var chatListener:ListenerRegistration?=null
 var onError: (String)->Unit = {}
 val signedIn get()=auth.currentUser!=null
 override suspend fun login(email:String,password:String,register:Boolean) {
  if(register) { auth.createUserWithEmailAndPassword(email.trim(),password).await(); auth.currentUser!!.sendEmailVerification().await(); error("Verification email sent. Open it, then press 'I verified my email'.") }
  auth.signInWithEmailAndPassword(email.trim(),password).await(); verify()
 }
 override suspend fun resend() { auth.currentUser?.sendEmailVerification()?.await() ?: error("Sign in first.") }
 override suspend fun verify() {
  val u=auth.currentUser ?: error("Sign in first."); u.reload().await()
  require(u.isEmailVerified) { "Verify your college email first." }; u.getIdToken(true).await()
  command("bootstrap"); subscribe(u.uid)
  runCatching { val token=FirebaseMessaging.getInstance().token.await(); db.collection("tokens").document(u.uid).set(mapOf("token" to token)).await() }
 }
 private fun subscribe(uid:String) {
  close(); flow.value=AppData(uid=uid)
  fun failure(e:Exception?) { if(e!=null) onError(e.localizedMessage ?: "Sync error") }
  listeners += db.collection("profiles").addSnapshotListener { s,e -> failure(e); if(s!=null) flow.value=flow.value.copy(profiles=s.documents.mapNotNull { d -> d.toObject(Profile::class.java)?.copy(id=d.id) }) }
  listeners += db.collection("wallets").document(uid).addSnapshotListener { s,e -> failure(e); if(s!=null) flow.value=flow.value.copy(balance=s.getLong("balance") ?: 0) }
  listeners += db.collection("sessions").whereArrayContains("members",uid).addSnapshotListener { s,e -> failure(e); if(s!=null) flow.value=flow.value.copy(sessions=s.documents.mapNotNull { d -> d.toObject(Session::class.java)?.copy(id=d.id) }.sortedByDescending { it.scheduledAt }) }
  listeners += db.collection("notices").orderBy("createdAt",com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100).addSnapshotListener { s,e -> failure(e); if(s!=null) flow.value=flow.value.copy(notices=s.documents.mapNotNull { d -> d.toObject(Notice::class.java)?.copy(id=d.id) }) }
  listeners += db.collection("wallets").document(uid).collection("ledger").orderBy("createdAt",com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100).addSnapshotListener { s,e -> failure(e); if(s!=null) flow.value=flow.value.copy(ledger=s.documents.mapNotNull { d -> d.toObject(Ledger::class.java)?.copy(id=d.id) }) }
  listeners += db.collection("public").document("fund").addSnapshotListener { s,e -> failure(e); if(s!=null) flow.value=flow.value.copy(fundPaise=s.getLong("paise") ?: 0) }
 }
 @Suppress("UNCHECKED_CAST")
 override suspend fun command(name:String,data:Map<String,Any>):Map<String,Any?> {
  return functions.getHttpsCallable(name).call(data).await().data as? Map<String,Any?> ?: emptyMap()
 }
 override fun watchChat(session:String) {
  chatListener?.remove(); flow.value=flow.value.copy(messages=emptyList())
  if(session.isBlank()) return
  chatListener=db.collection("sessions").document(session).collection("messages").orderBy("createdAt").limitToLast(100).addSnapshotListener { s,e ->
   if(e!=null) onError(e.localizedMessage ?: "Chat error")
   if(s!=null) flow.value=flow.value.copy(messages=s.documents.mapNotNull { d -> d.toObject(Message::class.java)?.copy(id=d.id) })
  }
 }
 override suspend fun photo(uri:Uri) {
  val uid=auth.currentUser?.uid ?: error("Sign in first")
  val ref=FirebaseStorage.getInstance().reference.child("avatars/$uid/profile")
  ref.putFile(uri).await(); command("profile",mapOf("photo" to ref.downloadUrl.await().toString()))
 }
 override fun logout() {
  close(); flow.value=AppData()
  // Invalidate the device token itself so pushes stop even if the token document delete never reaches the server.
  FirebaseMessaging.getInstance().deleteToken()
  val uid=auth.currentUser?.uid ?: return auth.signOut()
  // Sign out once the delete is sent under this account, or after 3 s if offline (the delete then waits for the next sign-in).
  val signOut=java.util.concurrent.atomic.AtomicBoolean(false).let { done -> { if(done.compareAndSet(false,true)) auth.signOut() } }
  db.collection("tokens").document(uid).delete().addOnCompleteListener { signOut() }
  android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ signOut() },3000)
 }
 override fun close() { listeners.forEach { it.remove() }; listeners.clear(); chatListener?.remove(); chatListener=null }
}
