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

// Meeting-location search, device location and local notifications used by the booking flow.

// Also the channel FCM uses for background pushes (see AndroidManifest), so create it at startup too.
fun createSessionChannel(context: android.content.Context) {
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
  context.getSystemService(android.app.NotificationManager::class.java)?.createNotificationChannel(android.app.NotificationChannel("sessions", "Kairos Sessions", android.app.NotificationManager.IMPORTANCE_HIGH))
 }
}

fun sendLocalNotification(context: android.content.Context, title: String, message: String) {
 if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
 val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
 createSessionChannel(context)
 val intent = android.app.PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
 val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
  android.app.Notification.Builder(context, "sessions")
 } else {
  @Suppress("DEPRECATION")
  android.app.Notification.Builder(context)
 }
 builder.setSmallIcon(R.drawable.ic_notification)
  .setContentTitle(title)
  .setContentText(message)
  .setStyle(android.app.Notification.BigTextStyle().bigText(message))
  .setContentIntent(intent)
  .setAutoCancel(true)
 manager.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
}

/** Calls back with the device's address, or null when no location fix is available. Never substitutes a made-up place. */
fun getCurrentLocationAddress(context: android.content.Context, onResult: (String?) -> Unit) {
 val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return onResult(null)
 val deliver: (android.location.Location?) -> Unit = { loc ->
  if (loc == null) onResult(null)
  else kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
   val address = reverseGeocode(loc.latitude, loc.longitude)
   kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(address) }
  }
 }
 try {
  val providers = lm.getProviders(true)
  // A recent cached fix is fine; otherwise ask the provider for a fresh one (phones often have no cached fix).
  val cached = providers.mapNotNull { lm.getLastKnownLocation(it) }.filter { System.currentTimeMillis() - it.time < 10 * 60000 }.minByOrNull { it.accuracy }
  if (cached != null) return deliver(cached)
  // Fused blends Wi-Fi, cell and GPS; fall back to the single providers on older or Play-less devices.
  val provider = listOf("fused", android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.GPS_PROVIDER).firstOrNull { it in providers } ?: return onResult(null)
  if (Build.VERSION.SDK_INT >= 30) {
   lm.getCurrentLocation(provider, null, context.mainExecutor) { deliver(it) }
  } else {
   @Suppress("DEPRECATION")
   lm.requestSingleUpdate(provider, { deliver(it) }, android.os.Looper.getMainLooper())
  }
 } catch (e: SecurityException) {
  onResult(null)
 }
}

fun reverseGeocode(lat: Double, lon: Double): String {
 return try {
  val url = java.net.URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon")
  val conn = url.openConnection() as java.net.HttpURLConnection
  conn.requestMethod = "GET"
  conn.setRequestProperty("User-Agent", "KairosCampus/1.0")
  conn.connectTimeout = 3000
  conn.readTimeout = 3000
  if (conn.responseCode == 200) {
   val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
   val obj = org.json.JSONObject(jsonText)
   val displayName = obj.optString("display_name", "")
   if (displayName.isNotBlank()) displayName else "Current Location (Lat: %.4f, Lon: %.4f)".format(lat, lon)
  } else "Current Location (Lat: %.4f, Lon: %.4f)".format(lat, lon)
 } catch (e: Exception) {
  "Current Location (Lat: %.4f, Lon: %.4f)".format(lat, lon)
 }
}

val campusLocations = listOf(
 "Campus Library - Main Reading Hall",
 "Student Center & Food Court",
 "Central Lawn & Amphitheatre",
 "NMIMS Main Gate Entrance",
 "SVKM Auditorium Lobby",
 "Computer Engineering Lab 302",
 "Management Block Lounge",
 "Sports Complex & Gym Hall",
 "Discussion Room 2 - Library 2nd Floor",
 "Cafeteria Coffee Corner - Ground Floor"
)

suspend fun searchMapsPlaces(query: String): List<Pair<String, String>> {
 if (query.trim().length < 2) return emptyList()
 return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
  try {
   val encoded = Uri.encode(query.trim())
   val url = java.net.URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=5&addressdetails=1")
   val conn = url.openConnection() as java.net.HttpURLConnection
   conn.requestMethod = "GET"
   conn.setRequestProperty("User-Agent", "KairosCampus/1.0")
   conn.connectTimeout = 3000
   conn.readTimeout = 3000
   if (conn.responseCode == 200) {
    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
    val jsonArray = org.json.JSONArray(jsonText)
    val list = mutableListOf<Pair<String, String>>()
    for (i in 0 until jsonArray.length()) {
     val obj = jsonArray.getJSONObject(i)
     val displayName = obj.optString("display_name", "")
     val name = obj.optString("name", "").ifBlank { displayName.split(",").firstOrNull() ?: "" }
     if (displayName.isNotBlank()) {
      list.add(Pair(name, displayName))
     }
    }
    list
   } else emptyList()
  } catch (e: Exception) {
   emptyList()
  }
 }
}

@Composable
fun LocationSearchInput(
 location: String,
 onLocationChange: (String) -> Unit,
 modifier: Modifier = Modifier
) {
 val context = LocalContext.current
 var isExpanded by remember { mutableStateOf(false) }
 var mapSuggestions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
 var isSearching by remember { mutableStateOf(false) }
 var isLocating by remember { mutableStateOf(false) }

 val fetchLocation = {
  isLocating = true
  getCurrentLocationAddress(context) { address ->
   isLocating = false
   if (address != null) { onLocationChange(address); isExpanded = false }
   else android.widget.Toast.makeText(context, "Couldn't get your location. Turn on Location or type a place.", android.widget.Toast.LENGTH_LONG).show()
  }
 }

 val locationPermissionLauncher = rememberLauncherForActivityResult(
  ActivityResultContracts.RequestMultiplePermissions()
 ) { permissions ->
  val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
  // Once denied permanently, Android returns here without showing a prompt, so always explain.
  if (granted) fetchLocation()
  else android.widget.Toast.makeText(context, "Location permission is off. Allow it in Settings, or type a place.", android.widget.Toast.LENGTH_LONG).show()
 }

 LaunchedEffect(location) {
  if (location.trim().length >= 2) {
   isSearching = true
   kotlinx.coroutines.delay(250)
   mapSuggestions = searchMapsPlaces(location)
   isSearching = false
  } else {
   mapSuggestions = emptyList()
   isSearching = false
  }
 }

 val filteredCampus = remember(location) {
  if (location.isBlank()) campusLocations
  else campusLocations.filter { it.contains(location, ignoreCase = true) }
 }

 val openInMaps = { queryStr: String ->
  val q = if (queryStr.isNotBlank()) queryStr else "NMIMS Mumbai"
  val mapUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))
  val intent = Intent(Intent.ACTION_VIEW, mapUri)
  runCatching { context.startActivity(intent) }.onFailure {
   val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))
   runCatching { context.startActivity(geoIntent) }
  }
 }

 val triggerLocationFetch = {
  val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
  val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
  if (hasFine || hasCoarse) {
   fetchLocation()
  } else {
   locationPermissionLauncher.launch(
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
   )
  }
 }

 Column(modifier = modifier.fillMaxWidth()) {
  OutlinedTextField(
   value = location,
   onValueChange = {
    onLocationChange(it.take(120))
    isExpanded = true
   },
   label = { Text("Meeting location") },
   placeholder = { Text("Search a campus spot or place") },
   leadingIcon = { Icon(Icons.Default.Place, contentDescription = "Location", tint = MaterialTheme.colorScheme.primary) },
   trailingIcon = {
    if (location.isNotBlank()) {
     IconButton(onClick = { onLocationChange(""); isExpanded = true }) {
      Icon(Icons.Default.Clear, contentDescription = "Clear")
     }
    }
   },
   singleLine = true,
   modifier = Modifier
    .fillMaxWidth()
    .onFocusChanged { focusState ->
     if (focusState.isFocused) isExpanded = true
    }
  )
  Row(verticalAlignment = Alignment.CenterVertically) {
   TextButton(enabled = !isLocating, onClick = { triggerLocationFetch() }) {
    if (isLocating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    else Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp)); Text(if (isLocating) "Locating…" else "Current location")
   }
   TextButton(onClick = { openInMaps(location) }) {
    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp)); Text("Maps")
   }
  }

  if (isExpanded && (filteredCampus.isNotEmpty() || mapSuggestions.isNotEmpty() || location.isNotBlank() || isSearching)) {
   Card(
    modifier = Modifier
     .fillMaxWidth()
     .padding(top = 4.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
   ) {
    Column(Modifier.padding(vertical = 4.dp)) {
     ListItem(
      headlineContent = { Text("Use Current Location", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
      supportingContent = { Text("Auto-detect GPS / Network location") },
      leadingContent = { Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      modifier = Modifier.fillMaxWidth().clickable {
       triggerLocationFetch()
      }
     )
     HorizontalDivider()

     if (location.isNotBlank()) {
      ListItem(
       headlineContent = { Text("Search \"$location\" on Google Maps", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
       supportingContent = { Text("Open direct Google Maps app/web search") },
       leadingContent = { Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
       modifier = Modifier.fillMaxWidth().clickable {
        openInMaps(location)
        isExpanded = false
       }
      )
      HorizontalDivider()
     }

     if (isSearching) {
      Row(
       modifier = Modifier.fillMaxWidth().padding(12.dp),
       verticalAlignment = Alignment.CenterVertically,
       horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
       CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
       Text("Searching places…", style = MaterialTheme.typography.bodySmall)
      }
     }

     if (mapSuggestions.isNotEmpty()) {
      Text("Place suggestions • OpenStreetMap", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
      mapSuggestions.forEach { (name, address) ->
       ListItem(
        headlineContent = { Text(name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(address, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable {
         onLocationChange(address)
         isExpanded = false
        }
       )
      }
      HorizontalDivider()
     }

     if (filteredCampus.isNotEmpty()) {
      Text("Campus Locations & Landmarks", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
      filteredCampus.take(4).forEach { campusLoc ->
       ListItem(
        headlineContent = { Text(campusLoc, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
        modifier = Modifier.fillMaxWidth().clickable {
         onLocationChange(campusLoc)
         isExpanded = false
        }
       )
      }
     }
    }
   }
  }
 }
}
