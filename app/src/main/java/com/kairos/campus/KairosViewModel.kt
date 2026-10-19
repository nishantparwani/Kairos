package com.kairos.campus

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class KairosViewModel(app: Application): AndroidViewModel(app) {
 val problem=MutableStateFlow("")
 val busy=MutableStateFlow(false)
 private val configured=BuildConfig.DEMO || FirebaseApp.getApps(app).isNotEmpty()
 val repository:Repository = if(BuildConfig.DEMO || !configured) DemoRepository(app) else FirebaseRepository()
 val state=if(configured) repository.state else MutableStateFlow(AppData())
 init { if(!configured) problem.value="Live setup missing: add app/google-services.json, sync and rebuild. Use demoDebug to try the app."; (repository as? FirebaseRepository)?.let { live -> live.onError={problem.value=it}; if(live.signedIn) run { live.verify() } } } // restore a saved live sign-in on launch
 fun run(block:suspend ()->Unit) { if(busy.value)return; if(!configured)return; busy.value=true; viewModelScope.launch { try { block() } catch(e:Exception) { problem.value=e.localizedMessage ?: "Something went wrong" } finally { busy.value=false } } }
 fun login(email:String,password:String,register:Boolean)=run { repository.login(email,password,register) }
 fun command(name:String,data:Map<String,Any> = emptyMap(),done:(Map<String,Any?>)->Unit = {})=run { done(repository.command(name,data)) }
 fun photo(uri:Uri)=run { repository.photo(uri) }
 override fun onCleared() { repository.close() }
}
