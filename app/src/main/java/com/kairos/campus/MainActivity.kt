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

class MainActivity:ComponentActivity(),PaymentResultWithDataListener {
 private var model:KairosViewModel?=null
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState); enableEdgeToEdge()
  setContent { val vm:KairosViewModel=viewModel(); model=vm; KairosApp(vm,this) }
 }
 fun checkout(order:Map<String,Any?>) {
  try {
   val checkout=Checkout(); checkout.setKeyID(order["keyId"] as String)
   checkout.open(this,JSONObject().put("name","Kairos Campus").put("description","120 Time Credits").put("currency","INR").put("amount",order["amount"]).put("order_id",order["orderId"]))
  } catch(e:Exception) { model?.problem?.value=e.localizedMessage ?: "Checkout unavailable" }
 }
 override fun onPaymentSuccess(paymentId:String?,data:PaymentData?) {
  model?.command("verifyPayment",mapOf("orderId" to (data?.orderId ?: ""),"paymentId" to (paymentId ?: ""),"signature" to (data?.signature ?: "")))
 }
 override fun onPaymentError(code:Int,response:String?,data:PaymentData?) { model?.problem?.value="Payment not completed. No credits added. You can retry the pending order from Wallet." }
}

@Composable
fun KairosApp(vm:KairosViewModel,activity:MainActivity) {
 val data by vm.state.collectAsState(); val busy by vm.busy.collectAsState(); val problem by vm.problem.collectAsState()
 var tab by rememberSaveable { mutableIntStateOf(0) }; var chat by rememberSaveable { mutableStateOf("") }; var booking by remember { mutableStateOf<Profile?>(null) }
 BackHandler(enabled=chat.isNotBlank()) { chat="";vm.repository.watchChat("") }
 val systemDark=isSystemInDarkTheme(); var dark by rememberSaveable { mutableStateOf(systemDark) }
 val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
 LaunchedEffect(data.uid) {
  if (data.uid.isNotBlank()) {
   val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
   if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
   permissionsLauncher.launch(perms.toTypedArray())
  }
 }
 MaterialTheme(colorScheme=if(dark) darkColorScheme(primary=Color(0xFF87D6BD)) else lightColorScheme(primary=Color(0xFF176B5B),secondary=Color(0xFF9D661E),background=Color(0xFFF5F7F2),surface=Color(0xFFFFFFFF))) {
  Surface(Modifier.fillMaxSize()) {
   if(data.uid.isBlank()) Login(vm,busy) else Scaffold(
    topBar={ Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=12.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically) {
      if(chat.isNotBlank()) IconButton(onClick={chat="";vm.repository.watchChat("")}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back") }
      Column(Modifier.weight(1f)) { Text("KAIROS",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black); Text(if(BuildConfig.DEMO) "DEMO • Campus skill exchange" else "Campus skill exchange",style=MaterialTheme.typography.labelMedium) }
      IconButton(onClick={dark=!dark}) { Icon(Icons.Default.DarkMode,"Toggle theme") }
     }
     if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    } },
    bottomBar={ if(chat.isBlank()) NavigationBar {
     val labels=listOf("Explore","Sessions","Board","Wallet","Profile"); val icons=listOf(Icons.Default.Explore,Icons.Default.Event,Icons.Default.Campaign,Icons.Default.AccountBalanceWallet,Icons.Default.Person)
     labels.forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],label)},label={Text(label,maxLines=1)}) }
    } }
   ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
     if(chat.isNotBlank()) Chat(data,chat,vm,busy) else when(tab) {
      0 -> Explore(data,onBook={booking=it})
      1 -> Sessions(data,vm,busy,onChat={chat=it;vm.repository.watchChat(it)})
      2 -> Board(data,vm,busy)
      3 -> Wallet(data,vm,busy,activity)
      4 -> ProfileScreen(data,vm,busy)
     }
    }
   }
   if(problem.isNotBlank()) AlertDialog(onDismissRequest={vm.problem.value=""},title={Text("Kairos")},text={Text(problem)},confirmButton={TextButton(onClick={vm.problem.value=""}){Text("OK")}})
   booking?.let { mentor -> Booking(mentor,vm,busy,onClose={booking=null},onBooked={booking=null;tab=1}) }
  }
 }
}

@Composable
fun Login(vm:KairosViewModel,busy:Boolean) {
 var email by rememberSaveable { mutableStateOf("") }; var password by rememberSaveable { mutableStateOf("") }
 Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.Center) {
  Text("KAIROS",style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)
  Text("Your time. Someone else's breakthrough.",style=MaterialTheme.typography.headlineSmall)
  Spacer(Modifier.height(24.dp)); Text("Learn from your campus. Share what you know. Earn time credits.")
  Spacer(Modifier.height(24.dp))
  if(!BuildConfig.DEMO) {
   OutlinedTextField(email,{email=it},label={Text("College email")},singleLine=true,modifier=Modifier.fillMaxWidth())
   OutlinedTextField(password,{password=it},label={Text("Password (at least 8 characters)")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
   Button(onClick={vm.login(email,password,false)},enabled=!busy && email.isNotBlank() && password.length>=8,modifier=Modifier.fillMaxWidth()){Text("Sign in")}
   OutlinedButton(onClick={vm.login(email,password,true)},enabled=!busy && email.isNotBlank() && password.length>=8,modifier=Modifier.fillMaxWidth()){Text("Create account")}
   TextButton(onClick={vm.run { vm.repository.verify() }},enabled=!busy){Text("I verified my email")}
   TextButton(onClick={vm.run { vm.repository.resend() }},enabled=!busy){Text("Resend verification")}
  } else Button(onClick={vm.login("","",false)},enabled=!busy){Text("Enter demo")}
  if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
 }
}

@Composable fun Panel(title:String,content:@Composable ColumnScope.()->Unit) {
 Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);content() } }
}
@Composable fun Empty(text:String) { Text(text,Modifier.padding(24.dp),style=MaterialTheme.typography.bodyLarge) }
fun date(ms:Long):String=DateTimeFormatter.ofPattern("dd MMM, hh:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))
