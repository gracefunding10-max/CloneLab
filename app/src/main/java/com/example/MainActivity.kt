package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.clonelab.data.ClonedApp
import com.example.clonelab.ui.CloneLabViewModel
import com.example.clonelab.ui.CloneSettingsScreen
import com.example.clonelab.ui.HomeScreen
import com.example.clonelab.ui.LogsScreen
import com.example.clonelab.ui.SandboxScreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme

enum class MainTab {
    CONTAINERS,
    SANDBOX,
    LOGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: CloneLabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CloneLabApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CloneLabApp(viewModel: CloneLabViewModel) {
    var currentTab by remember { mutableStateOf(MainTab.CONTAINERS) }
    var activeEditingClone by remember { mutableStateOf<ClonedApp?>(null) }

    // If detail screen is open, back button returns to HomeScreen
    if (activeEditingClone != null) {
        BackHandler {
            activeEditingClone = null
        }
        CloneSettingsScreen(
            clone = activeEditingClone!!,
            viewModel = viewModel,
            onBack = { activeEditingClone = null },
            onLaunchSandbox = {
                viewModel.selectClone(activeEditingClone)
                activeEditingClone = null
                currentTab = MainTab.SANDBOX
            },
            modifier = Modifier.fillMaxSize().background(DarkBackground)
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.CONTAINERS,
                        onClick = { currentTab = MainTab.CONTAINERS },
                        icon = { Icon(Icons.Default.Layers, contentDescription = "Containers") },
                        label = { Text("Containers") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan.copy(alpha = 0.2f),
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("nav_containers")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.SANDBOX,
                        onClick = { currentTab = MainTab.SANDBOX },
                        icon = { Icon(Icons.Default.DeveloperMode, contentDescription = "Sandbox") },
                        label = { Text("Sandbox") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan.copy(alpha = 0.2f),
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("nav_sandbox")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.LOGS,
                        onClick = { currentTab = MainTab.LOGS },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = "Logs") },
                        label = { Text("Diagnostics") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan.copy(alpha = 0.2f),
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("nav_logs")
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            when (currentTab) {
                MainTab.CONTAINERS -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = { clone ->
                            activeEditingClone = clone
                        },
                        onNavigateToSandbox = { clone ->
                            viewModel.selectClone(clone)
                            currentTab = MainTab.SANDBOX
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(DarkBackground)
                    )
                }

                MainTab.SANDBOX -> {
                    SandboxScreen(
                        viewModel = viewModel,
                        onBack = { currentTab = MainTab.CONTAINERS },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(DarkBackground)
                    )
                }

                MainTab.LOGS -> {
                    LogsScreen(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(DarkBackground)
                    )
                }
            }
        }
    }
}
