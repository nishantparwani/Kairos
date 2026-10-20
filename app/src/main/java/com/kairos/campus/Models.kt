package com.kairos.campus

data class Profile(val id: String = "", val name: String = "", val skills: String = "", val availability: String = "", val bio: String = "", val photo: String = "", val ratingSum: Long = 0, val ratingCount: Long = 0) {
 val rating: String get() = if (ratingCount == 0L) "New mentor" else "%.1f / 5 (%d)".format(ratingSum.toDouble()/ratingCount, ratingCount)
}
data class Session(val id: String = "", val learner: String = "", val mentor: String = "", val members: List<String> = emptyList(), val topic: String = "", val minutes: Long = 60, val scheduledAt: Long = 0, val location: String = "Campus Library", val status: String = "requested", val confirmations: List<String> = emptyList(), val rated: List<String> = emptyList())
data class Notice(val id: String = "", val author: String = "", val title: String = "", val body: String = "", val createdAt: Long = 0)
data class Message(val id: String = "", val sender: String = "", val text: String = "", val createdAt: Long = 0)
data class Ledger(val id: String = "", val label: String = "", val amount: Long = 0, val createdAt: Long = 0)
data class AppData(val uid: String = "", val profiles: List<Profile> = emptyList(), val balance: Long = 0, val sessions: List<Session> = emptyList(), val notices: List<Notice> = emptyList(), val ledger: List<Ledger> = emptyList(), val messages: List<Message> = emptyList(), val fundPaise: Long = 0)

object SkillMatcher {
 // Filler words otherwise match bios ("help with python" matched anyone whose bio says "with").
 private val stopWords = setOf("help", "with", "and", "the", "for", "need", "want", "learn", "how", "about", "some", "someone", "teach", "can", "you", "please")
 private val aliases = mapOf("inheritance" to "java", "oop" to "java", "derivative" to "calculus", "integral" to "calculus", "editing" to "video", "android" to "kotlin", "pandas" to "python", "dsa" to "structures", "structures" to "dsa", "ml" to "machine", "ai" to "machine", "algo" to "algorithms", "neural" to "machine")
 fun rank(query: String, peers: List<Profile>): List<Profile> {
  val words = query.lowercase().split(Regex("[^a-z0-9+#]+" )).filter { it.length >= 2 && it !in stopWords }
  val tokens = (words + words.mapNotNull { aliases[it] }).toSet()
  fun score(p: Profile) = tokens.count { p.skills.lowercase().contains(it) } * 10 + tokens.count { p.bio.lowercase().contains(it) }
  return peers.filter { tokens.isEmpty() || score(it) > 0 }.sortedWith(compareByDescending<Profile> { score(it) }.thenByDescending { if(it.ratingCount == 0L) 0.0 else it.ratingSum.toDouble()/it.ratingCount })
 }
}
