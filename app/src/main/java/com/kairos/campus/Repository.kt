package com.kairos.campus

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface Repository {
 val state: StateFlow<AppData>
 suspend fun login(email: String, password: String, register: Boolean)
 suspend fun verify()
 suspend fun resend()
 suspend fun command(name: String, data: Map<String, Any> = emptyMap()): Map<String, Any?>
 fun watchChat(session: String)
 suspend fun photo(uri: Uri)
 fun logout()
 fun close() {}
}

/** Offline classroom simulation. Never used to authorize real credits or money. */
class DemoRepository(context: Context): Repository {
 private val prefs = context.getSharedPreferences("kairos_demo", Context.MODE_PRIVATE)
 private val gson = Gson()
 private fun initial() = AppData(uid="yash", profiles=listOf(
  Profile("yash", "Yashwardhan", "Kotlin, Android", "Weekdays after 5 PM", "Happy to help with Android apps."),
  Profile("harsh", "Harsh", "Java, Python", "Weekdays 6–8 PM", "OOP, inheritance and interview practice", ratingSum=24, ratingCount=5),
  Profile("aditi", "Aditi", "Calculus, Mathematics", "Saturday afternoon", "Learn derivatives with practical examples", ratingSum=19, ratingCount=4),
  Profile("nishant", "Nishant", "Video editing, Design", "Sunday 11 AM", "Bring your creative ideas", ratingSum=14, ratingCount=3),
  Profile("abhik", "Abhik", "AI/ML, DSA", "Weekdays 4–7 PM", "Expert in AI/ML, Neural Networks, Data Structures & Algorithms. Available for project guidance, code reviews & interview prep.", ratingSum=25, ratingCount=5)
 ), balance=120, notices=listOf(Notice("welcome", "harsh", "Java revision circle", "Meet in the campus library. Post a request to arrange a time.", System.currentTimeMillis())), ledger=listOf(Ledger("bonus", "Joining bonus",120,System.currentTimeMillis())))
 // Saved demo data predates newly added sample students, so merge any missing ones in.
 private var saved = runCatching { gson.fromJson(prefs.getString("data", null), AppData::class.java) }.getOrNull()?.let { d -> d.copy(profiles=d.profiles+initial().profiles.filter { p -> d.profiles.none { it.id==p.id } }) } ?: initial()
 private val flow = MutableStateFlow(saved)
 override val state: StateFlow<AppData> = flow
 private var chat = ""
 private val chats = mutableMapOf<String, List<Message>>()
 init { val raw = prefs.getString("chats", "{}"); val obj = org.json.JSONObject(raw ?: "{}"); obj.keys().forEach { key -> chats[key] = gson.fromJson(obj.getString(key), Array<Message>::class.java).toList() } }
 private fun save(v: AppData) { saved=v; flow.value=v; prefs.edit().putString("data",gson.toJson(v)).putString("chats",org.json.JSONObject(chats.mapValues { gson.toJson(it.value) }).toString()).apply() }
 override suspend fun login(email:String,password:String,register:Boolean) { save(saved.copy(uid="yash")) }
 override suspend fun verify() {}
 override suspend fun resend() {}
 override fun logout() { flow.value = AppData() }
 override fun watchChat(session:String) { chat=session; flow.value=saved.copy(messages=chats[session].orEmpty()) }
 override suspend fun photo(uri:Uri) { command("profile", mapOf("photo" to uri.toString())) }
 override suspend fun command(name:String,data:Map<String,Any>):Map<String,Any?> {
  val now=System.currentTimeMillis(); fun id()=UUID.randomUUID().toString()
  var v=saved
  fun entry(label:String,amount:Long) { v=v.copy(balance=v.balance+amount,ledger=listOf(Ledger(id(),label,amount,now))+v.ledger) }
  when(name) {
   "profile" -> { v=v.copy(profiles=v.profiles.map { if(it.id==v.uid) it.copy(name=data["name"] as? String ?: it.name, skills=data["skills"] as? String ?: it.skills,availability=data["availability"] as? String ?: it.availability,bio=data["bio"] as? String ?: it.bio,photo=data["photo"] as? String ?: it.photo) else it }) }
   "book" -> {
    val minutes=(data["minutes"] as Number).toLong(); require(minutes in 15..180 && minutes%15==0L) { "Choose 15–180 minutes in steps of 15." }
    require(v.balance>=minutes) { "Not enough credits." }
    require((data["scheduledAt"] as Number).toLong()>now) { "Choose a future date and time." }
    entry("Reserved for ${data["topic"]}",-minutes)
    v=v.copy(sessions=listOf(Session(id(),v.uid,data["mentor"] as String,listOf(v.uid,data["mentor"] as String),data["topic"] as String,minutes,(data["scheduledAt"] as Number).toLong(),data["location"] as String))+v.sessions)
   }
   "session" -> {
    val s=v.sessions.first { it.id==data["id"] }; val action=data["action"]
    val updated=when(action) {
     "accept" -> { require(s.status=="requested"); s.copy(status="accepted") }
     "cancel" -> { require(s.status=="requested" || (s.status=="accepted" && s.confirmations.isEmpty())); entry("Session refund",s.minutes); s.copy(status="cancelled") }
     "complete" -> { require(s.status=="accepted"); if(s.confirmations.contains(v.uid)) s.copy(status="completed",confirmations=s.members) else s.copy(confirmations=listOf(v.uid)) }
     else -> error("Unknown action")
    }; v=v.copy(sessions=v.sessions.map { if(it.id==s.id) updated else it })
   }
   "rate" -> {
    val s=v.sessions.first { it.id==data["id"] }; require(s.status=="completed" && !s.rated.contains(v.uid))
    val stars=(data["stars"] as Number).toLong(); require(stars in 1..5)
    v=v.copy(sessions=v.sessions.map { if(it.id==s.id) it.copy(rated=it.rated+v.uid) else it }, profiles=v.profiles.map { if(it.id==s.mentor) it.copy(ratingSum=it.ratingSum+stars,ratingCount=it.ratingCount+1) else it })
   }
   "notice" -> { v=v.copy(notices=listOf(Notice(id(),v.uid,data["title"] as String,data["body"] as String,now))+v.notices) }
   "message" -> { val key=data["id"] as String; chats[key]=chats[key].orEmpty()+Message(id(),v.uid,data["text"] as String,now); v=v.copy(messages=chats[key].orEmpty()) }
   "topup" -> {
    val month=java.time.YearMonth.now().toString(); val count=if(prefs.getString("month","")==month) prefs.getInt("purchases",0) else 0
    require(count<2) { "Demo monthly limit: 2 packs." }; prefs.edit().putString("month",month).putInt("purchases",count+1).apply()
    entry("Simulated top-up • no payment",120); v=v.copy(fundPaise=v.fundPaise+1000)
   }
   else -> error("Unsupported demo action")
  }
  save(v); return emptyMap()
 }
}
