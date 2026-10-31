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
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
        val prefs = Prefs(this)
        I18n.lang = prefs.lang
        setContent {
            key(prefs.lang) {
                MargyTheme { App(prefs) }
            }
        }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    HORIZON("Моды", Icons.Outlined.Extension),
    MODRINTH("Modrinth", Icons.Outlined.Public),
    TOUCH("Управление", Icons.Outlined.Gamepad),
    BUILD("Сборка", Icons.Outlined.Build),
    SETTINGS("Ещё", Icons.Outlined.Settings),
}

/** Лаунчер всегда горизонтальный: вкладки — слева в рейле. */
@Composable
fun App(prefs: Prefs) {
    var tab by remember { mutableStateOf(Tab.HORIZON) }

    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            header = {
                Spacer(Modifier.height(6.dp))
                Icon(Icons.Rounded.AutoAwesomeMotion, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp))
            },
        ) {
            Tab.entries.forEach { t ->
                NavigationRailItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = { Icon(t.icon, contentDescription = I18n.t(t.title)) },
                    label = { Text(I18n.t(t.title)) },
                    alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (tab) {
                Tab.HORIZON -> HorizonScreen(prefs)
                Tab.MODRINTH -> ModrinthScreen(prefs)
                Tab.TOUCH -> TouchScreen(prefs)
                Tab.BUILD -> BuildScreen(prefs)
                Tab.SETTINGS -> SettingsScreen(prefs)
            }
        }
    }
}
