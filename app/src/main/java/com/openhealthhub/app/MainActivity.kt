package com.openhealthhub.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.openhealthhub.app.core.database.DailyHealthEntity
import com.openhealthhub.app.core.healthconnect.HealthConnectManager
import com.openhealthhub.app.core.sync.HealthSyncRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{OpenHealthHubUi()}}}
}
@HiltViewModel class MainViewModel @Inject constructor(val health:HealthConnectManager,private val repo:HealthSyncRepository):ViewModel(){
 val days=repo.observeDaily().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 var status by mutableStateOf<String?>(null); private set
 fun sync(){viewModelScope.launch{status=repo.sync().fold({"Synchronization complete"},{"Sync failed: "+(it.message?:"unknown error")})}}
}
@Composable fun OpenHealthHubUi(vm:MainViewModel=hiltViewModel()){
 val nav=rememberNavController(); val routes=listOf("dashboard","activity","sleep","body","settings")
 Scaffold(bottomBar={NavigationBar{routes.forEach{route->NavigationBarItem(selected=false,onClick={nav.navigate(route){launchSingleTop=true}},icon={},label={Text(route.replaceFirstChar{it.uppercase()})})}}}){padding->
  NavHost(navController=nav,startDestination="dashboard",modifier=Modifier.padding(padding)){
   composable("dashboard"){Dashboard(vm)}
   composable("activity"){MetricList(vm,"Activity"){d->"Steps: "+d.steps+" · Distance: "+String.format("%.1f",d.distanceMeters/1000)+" km · Active: "+String.format("%.0f",d.activeCalories)+" kcal"}}
   composable("sleep"){MetricList(vm,"Sleep"){d->"Sleep: "+(d.sleepMinutes?:0)+" min · Resting HR: "+(d.restingHeartRate?.toString()?:"—")}}
   composable("body"){MetricList(vm,"Body"){d->"Weight: "+(d.weightKg?.let{String.format("%.1f",it)}?:"—")+" kg · Body fat: "+(d.bodyFatPercent?.let{String.format("%.1f",it)}?:"—")+"%"}}
   composable("settings"){Settings(vm)}
  }
 }
}
@Composable private fun Dashboard(vm:MainViewModel){
 val days by vm.days.collectAsStateWithLifecycle(); val d=days.firstOrNull()
 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Open Health Hub",style=MaterialTheme.typography.headlineMedium)
  if(d==null) Text("No health data imported yet.") else { Text("Today",style=MaterialTheme.typography.titleLarge);Text("Steps: "+d.steps);Text("Active calories: "+String.format("%.0f",d.activeCalories)+" kcal");Text("Sleep: "+(d.sleepMinutes?:0)+" min");Text("Weight: "+(d.weightKg?.let{String.format("%.1f kg",it)}?:"Unavailable"));Text("Resting HR: "+(d.restingHeartRate?.let{it.toString()+" bpm"}?:"Unavailable")) }
  vm.status?.let{Text(it)}
 }
}
@Composable private fun MetricList(vm:MainViewModel,title:String,line:(DailyHealthEntity)->String){
 val days by vm.days.collectAsStateWithLifecycle()
 Column(Modifier.fillMaxSize().padding(20.dp)){Text(title,style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(days,key={it.date}){d->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(d.date,style=MaterialTheme.typography.titleMedium);Text(line(d))}}}}}
}
@Composable private fun Settings(vm:MainViewModel){
 val scope=rememberCoroutineScope();var granted by remember{mutableStateOf(false)}
 val launcher=rememberLauncherForActivityResult(vm.health.permissionContract){scope.launch{granted=vm.health.hasPermissions()}}
 LaunchedEffect(Unit){granted=vm.health.hasPermissions()}
 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Settings",style=MaterialTheme.typography.headlineMedium);Text(if(vm.health.client==null)"Health Connect unavailable" else if(granted)"Health Connect connected" else "Health Connect permissions required")
  if(vm.health.client!=null&&!granted) Button(onClick={launcher.launch(vm.health.permissions)}){Text("Grant permissions")}
  Button(enabled=granted,onClick=vm::sync){Text("Sync now")};vm.status?.let{Text(it)}
 }
}
