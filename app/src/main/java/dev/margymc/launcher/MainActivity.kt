package dev.margymc.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.margymc.launcher.ui.*

class MainActivity : ComponentActivity() {
    private val storage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            storage.launch(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                )
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { MargyTheme { App() } }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    HORIZON("Моды", Icons.Outlined.Extension),
    MODRINTH("Modrinth", Icons.Outlined.Public),
    BUILD("Сборка", Icons.Outlined.Build),
    SETTINGS("Ещё", Icons.Outlined.Settings),
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var tab by remember { mutableStateOf(Tab.HORIZON) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.HORIZON -> HorizonScreen(prefs)
                Tab.MODRINTH -> ModrinthScreen(prefs)
                Tab.BUILD -> BuildScreen(prefs)
                Tab.SETTINGS -> SettingsScreen(prefs)
            }
        }
    }
}
