package com.kairos.campus

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

// Tab screens: Explore, Booking, Sessions, Chat, Board, Wallet and Profile.

@Composable fun Explore(data:AppData,onBook:(Profile)->Unit) {
 var query by rememberSaveable { mutableStateOf("") }; val peers=SkillMatcher.rank(query,data.profiles.filter { it.id!=data.uid })
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { Panel("Make time for a new skill") { Text("${data.balance} credits available • 1 credit = 1 minute");Text("Ask for help, find a peer, learn together.") } }
  item { OutlinedTextField(query,{query=it},label={Text("Try: help with Java inheritance")},modifier=Modifier.fillMaxWidth(),leadingIcon={Icon(Icons.Default.Search,null)}) }
  item { Text("Skill matching • keyword and synonym ranking",style=MaterialTheme.typography.labelMedium) }
  if(peers.isEmpty()) item { Empty("No matches yet. Try a broader skill or post on the board.") }
  items(peers,key={it.id}) { p -> Panel(p.name) {
   if(p.photo.isNotBlank()) AsyncImage(model=p.photo,contentDescription="${p.name}'s profile picture",modifier=Modifier.size(56.dp))
   Text(p.skills,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text(p.bio);Text(p.availability);Text("★ ${p.rating}")
   Button(onClick={onBook(p)}) { Text("Request session") }
  } }
 }
}

@Composable fun Booking(peer:Profile,vm:KairosViewModel,busy:Boolean,onClose:()->Unit,onBooked:()->Unit) {
 val context=LocalContext.current
 var topic by rememberSaveable { mutableStateOf("") }; var minutes by rememberSaveable { mutableIntStateOf(60) }; var location by rememberSaveable { mutableStateOf("Campus Library") }
 var whenMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()+86400000) }
 AlertDialog(onDismissRequest=onClose,title={Text("Learn with ${peer.name}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  OutlinedTextField(topic,{topic=it.take(120)},label={Text("What do you need help with?")})
  Text("$minutes minutes = $minutes credits")
  Slider(value=minutes.toFloat(),onValueChange={minutes=(it/15).toInt()*15},valueRange=15f..180f,steps=10)
  LocationSearchInput(location, onLocationChange = { location = it })
  OutlinedButton(onClick={ val cal=Calendar.getInstance().apply { timeInMillis=whenMs }; DatePickerDialog(context,{_,y,m,d -> cal.set(y,m,d); TimePickerDialog(context,{_,h,min -> cal.set(Calendar.HOUR_OF_DAY,h); cal.set(Calendar.MINUTE,min);cal.set(Calendar.SECOND,0);whenMs=cal.timeInMillis },cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),false).show() },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).apply { datePicker.minDate=System.currentTimeMillis(); datePicker.maxDate=System.currentTimeMillis()+90L*86400000 }.show() }) { Text(date(whenMs)) }
  Text("Credits are reserved now. The mentor accepts, then both of you confirm completion.")
 }},confirmButton={Button(enabled=!busy && topic.isNotBlank() && location.isNotBlank(),onClick={vm.command("book",mapOf("mentor" to peer.id,"topic" to topic.trim(),"minutes" to minutes,"scheduledAt" to whenMs,"location" to location.trim())){
  sendLocalNotification(context, "Session Requested", "Request sent to ${peer.name} for '${topic.trim()}' at ${location.trim()}.")
  onBooked()
 }}){Text("Send request")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable fun Sessions(data:AppData,vm:KairosViewModel,busy:Boolean,onChat:(String)->Unit) {
 val context=LocalContext.current
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { Text("Your learning journey",style=MaterialTheme.typography.headlineSmall) }
  if(data.sessions.isEmpty()) item { Empty("No sessions yet. Find a mentor in Explore.") }
  items(data.sessions,key={it.id}) { s -> Panel(s.topic) {
   val other=if(s.learner==data.uid) s.mentor else s.learner
   Text("With ${data.profiles.find { it.id==other }?.name ?: "Campus peer"} • ${s.minutes} minutes")
   Text(date(s.scheduledAt)); Text(s.location); Text(s.status.uppercase(),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick={onChat(s.id)}){Text("Chat")}
    TextButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(s.location+" NMIMS Mumbai")))) }.onFailure { vm.problem.value="No Maps app available on this device." }}) { Text("Map") }
   }
   if(s.status=="requested" && (s.mentor==data.uid || BuildConfig.DEMO)) Button(enabled=!busy,onClick={vm.command("session",mapOf("id" to s.id,"action" to "accept")){
    sendLocalNotification(context, "Session Accepted", "Session '${s.topic}' was accepted at ${s.location}.")
   }}) { Text(if(BuildConfig.DEMO) "Demo: mentor accepts" else "Accept request") }
   if(s.status=="accepted") {
    val confirmed=s.confirmations.contains(data.uid)
    Text("${s.confirmations.size}/2 completion confirmations")
    Button(enabled=!busy && (!confirmed || BuildConfig.DEMO),onClick={vm.command("session",mapOf("id" to s.id,"action" to "complete")){
     sendLocalNotification(context, "Session Confirmed", "Session '${s.topic}' completion confirmed! Time credits transferred.")
    }}) { Text(if(confirmed && BuildConfig.DEMO) "Demo: mentor confirms" else if(confirmed) "Waiting for peer" else "Confirm completed") }
   }
   if(s.status=="requested" || (s.status=="accepted" && s.confirmations.isEmpty())) TextButton(enabled=!busy,onClick={vm.command("session",mapOf("id" to s.id,"action" to "cancel")){
    sendLocalNotification(context, "Session Cancelled", "Session '${s.topic}' was cancelled. Time credits refunded.")
   }}) { Text("Cancel & refund") }
   if(s.status=="completed" && !s.rated.contains(data.uid)) {
    Text("Rate this session")
    Row { (1..5).forEach { star -> TextButton(enabled=!busy,onClick={vm.command("rate",mapOf("id" to s.id,"stars" to star))},contentPadding=PaddingValues(6.dp),modifier=Modifier.weight(1f)) { Text("$star ★") } } }
   }
  } }
 }
}

@Composable fun Chat(data:AppData,id:String,vm:KairosViewModel,busy:Boolean) {
 var text by rememberSaveable(id) { mutableStateOf("") }
 val list=rememberLazyListState()
 LaunchedEffect(data.messages.size) { if(data.messages.isNotEmpty()) list.animateScrollToItem(data.messages.size-1) }
 Column(Modifier.fillMaxSize().imePadding().padding(16.dp)) {
  Text("Session conversation",style=MaterialTheme.typography.titleLarge)
  if(BuildConfig.DEMO) Text("Demo chat: messages are stored locally; peers do not auto-reply.",style=MaterialTheme.typography.labelSmall)
  LazyColumn(Modifier.weight(1f),state=list,verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=12.dp)) {
   items(data.messages,key={it.id}) { msg -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(if(msg.sender==data.uid) "You" else data.profiles.find { it.id==msg.sender }?.name ?: "Peer",fontWeight=FontWeight.Bold);Text(msg.text);Text(date(msg.createdAt),style=MaterialTheme.typography.labelSmall) } } }
  }
  OutlinedTextField(text,{text=it.take(1000)},label={Text("Message")},modifier=Modifier.fillMaxWidth())
  Button(enabled=!busy && text.isNotBlank(),onClick={vm.command("message",mapOf("id" to id,"text" to text.trim())){text=""}},modifier=Modifier.fillMaxWidth()){Text("Send")}
 }
}

@Composable fun Board(data:AppData,vm:KairosViewModel,busy:Boolean) {
 var title by rememberSaveable { mutableStateOf("") }; var body by rememberSaveable { mutableStateOf("") }
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { Panel("Campus notice board") {
   OutlinedTextField(title,{title=it.take(120)},label={Text("Announcement or help request")},modifier=Modifier.fillMaxWidth())
   OutlinedTextField(body,{body=it.take(1000)},label={Text("Details, time and place")},modifier=Modifier.fillMaxWidth())
   Button(enabled=!busy && title.isNotBlank() && body.isNotBlank(),onClick={vm.command("notice",mapOf("title" to title.trim(),"body" to body.trim())) {title="";body=""}}){Text("Post to campus")}
  } }
  items(data.notices,key={it.id}) { n -> Panel(n.title) { Text(n.body); Text("${data.profiles.find { it.id==n.author }?.name ?: "Student"} • ${date(n.createdAt)}",style=MaterialTheme.typography.labelMedium) } }
 }
}

@Composable fun Wallet(data:AppData,vm:KairosViewModel,busy:Boolean,activity:MainActivity) {
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { Panel("Your time wallet") {
   Text("${data.balance}",style=MaterialTheme.typography.displayLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
   Text("available time credits • 1 credit = 1 minute")
   Text("Teach to earn. Learn to spend. No cash-out.")
   Text("120 credits / ₹100 • Up to 2 purchased packs per calendar month (UTC). Configurable pilot policy.")
   Button(enabled=!busy,onClick={vm.command(if(BuildConfig.DEMO) "topup" else "createOrder") { if(!BuildConfig.DEMO) activity.checkout(it) }}) { Text(if(BuildConfig.DEMO) "Simulate top-up (no charge)" else "Buy / resume 120-credit pack") }
   if(!BuildConfig.DEMO) OutlinedButton(enabled=!busy,onClick={vm.command("reconcilePayment"){vm.problem.value="Checked pending order. Captured payments are credited automatically."}}){Text("Recover pending payment")}
  } }
  item { Panel("Campus Skill Fund") {Text("₹%.2f recorded allocation".format(data.fundPaise/100.0));Text(if(BuildConfig.DEMO) "Illustrative demo allocation only." else "10% of captured pack revenue is recorded for the campus fund. This is an accounting allocation, not an automatic bank disbursement.") } }
  item { Text("Credit history",style=MaterialTheme.typography.titleLarge) }
  items(data.ledger,key={it.id}) { l -> ListItem(headlineContent={Text(l.label)},supportingContent={Text(date(l.createdAt))},trailingContent={Text(if(l.amount>0) "+${l.amount}" else "${l.amount}",fontWeight=FontWeight.Bold)}) }
 }
}

@Composable fun ProfileScreen(data:AppData,vm:KairosViewModel,busy:Boolean) {
 val profile=data.profiles.find { it.id==data.uid } ?: Profile(id=data.uid)
 var name by rememberSaveable(profile.id,profile.name) { mutableStateOf(profile.name) }; var skills by rememberSaveable(profile.id,profile.skills){mutableStateOf(profile.skills)}
 var availability by rememberSaveable(profile.id,profile.availability){mutableStateOf(profile.availability)}; var bio by rememberSaveable(profile.id,profile.bio){mutableStateOf(profile.bio)}
 val context=LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) { runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) };vm.photo(uri) } }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("Share your strengths",style=MaterialTheme.typography.headlineSmall)
  if(profile.photo.isNotBlank()) AsyncImage(model=profile.photo,contentDescription="Your profile picture",modifier=Modifier.size(90.dp))
  OutlinedButton(enabled=!busy,onClick={picker.launch(arrayOf("image/*"))}){Text("Choose profile photo")}
  OutlinedTextField(name,{name=it.take(60)},label={Text("Display name")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(skills,{skills=it.take(200)},label={Text("Skills (comma separated)")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(availability,{availability=it.take(200)},label={Text("Availability")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(bio,{bio=it.take(500)},label={Text("About you")},modifier=Modifier.fillMaxWidth())
  Text("Reputation: ${profile.rating}")
  Button(enabled=!busy && name.isNotBlank(),onClick={vm.command("profile",mapOf("name" to name.trim(),"skills" to skills,"availability" to availability,"bio" to bio)){vm.problem.value="Profile saved."}}){Text("Save profile")}
  TextButton(enabled=!busy,onClick={vm.repository.logout()}){Text("Sign out")}
 }
}
