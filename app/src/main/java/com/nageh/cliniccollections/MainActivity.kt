package com.nageh.cliniccollections

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nageh.cliniccollections.data.AppDatabase
import com.nageh.cliniccollections.data.ClinicDao
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.ui.AboutScreen
import com.nageh.cliniccollections.ui.BrandSplash
import com.nageh.cliniccollections.ui.ClinicDetailScreen
import com.nageh.cliniccollections.ui.ClinicTheme
import com.nageh.cliniccollections.ui.ClinicsScreen
import com.nageh.cliniccollections.ui.Emerald
import com.nageh.cliniccollections.ui.EmeraldDeep
import com.nageh.cliniccollections.ui.EmeraldSoft
import com.nageh.cliniccollections.ui.HomeScreen
import com.nageh.cliniccollections.ui.InvoiceDetailScreen
import com.nageh.cliniccollections.ui.InvoiceFormScreen
import com.nageh.cliniccollections.ui.ReportsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the platform splash window is installed.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val database = AppDatabase.get(applicationContext)
        setContent {
            // Invoice numbers and phone numbers must read left to right even when the
            // device language is Arabic.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                ClinicTheme {
                    Root(database.invoiceDao(), database.clinicDao())
                }
            }
        }
    }
}

/** Bottom navigation destinations. */
enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Clinics("Clinics", Icons.Default.LocalHospital),
    Reports("Reports", Icons.Default.Assessment),
    About("About", Icons.Default.Info)
}

/** Full-screen destinations pushed above the tabs; these hide the bottom bar. */
sealed interface Overlay {
    data class InvoiceForm(val id: Long? = null, val clinicId: Long? = null) : Overlay
    data class InvoiceDetail(val id: Long) : Overlay
    data class ClinicDetail(val id: Long) : Overlay
}

@Composable
private fun Root(dao: InvoiceDao, clinicDao: ClinicDao) {
    var showSplash by remember { mutableStateOf(true) }
    Crossfade(targetState = showSplash, label = "splash") { splash ->
        if (splash) {
            BrandSplash { showSplash = false }
        } else {
            App(dao, clinicDao)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(dao: InvoiceDao, clinicDao: ClinicDao) {
    var tab by remember { mutableStateOf(Tab.Home) }
    val stack = remember { mutableStateListOf<Overlay>() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Physical back: pop the overlay stack first, then fall back to the Home tab,
    // and only then let the system close the app.
    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.Home) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else tab = Tab.Home
    }

    val top = stack.lastOrNull()
    if (top != null) {
        when (top) {
            is Overlay.InvoiceForm -> InvoiceFormScreen(
                dao = dao,
                clinicDao = clinicDao,
                invoiceId = top.id,
                presetClinicId = top.clinicId,
                onDone = { stack.removeAt(stack.lastIndex) }
            )

            is Overlay.InvoiceDetail -> InvoiceDetailScreen(
                dao = dao,
                id = top.id,
                onBack = { stack.removeAt(stack.lastIndex) },
                onEdit = { stack.add(Overlay.InvoiceForm(id = top.id)) }
            )

            is Overlay.ClinicDetail -> ClinicDetailScreen(
                invoiceDao = dao,
                clinicDao = clinicDao,
                clinicId = top.id,
                onBack = { stack.removeAt(stack.lastIndex) },
                onOpenInvoice = { stack.add(Overlay.InvoiceDetail(it)) },
                onAddInvoice = { stack.add(Overlay.InvoiceForm(clinicId = top.id)) }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.Home -> "Clinic Collections"
                            Tab.Clinics -> "Clinic directory"
                            Tab.Reports -> "Monthly report"
                            Tab.About -> "About"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldSoft,
                    titleContentColor = EmeraldDeep
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 3.dp) {
                Tab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination,
                        onClick = { tab = destination },
                        icon = { Icon(destination.icon, destination.label) },
                        label = { Text(destination.label) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldDeep,
                            selectedTextColor = EmeraldDeep,
                            indicatorColor = EmeraldSoft
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Home) {
                ExtendedFloatingActionButton(
                    onClick = { stack.add(Overlay.InvoiceForm()) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add invoice") },
                    containerColor = Emerald,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val inner = PaddingValues(bottom = padding.calculateBottomPadding())
            when (tab) {
                Tab.Home -> HomeScreen(dao, inner) { stack.add(Overlay.InvoiceDetail(it)) }
                Tab.Clinics -> ClinicsScreen(clinicDao, dao, inner) {
                    stack.add(Overlay.ClinicDetail(it))
                }
                Tab.Reports -> ReportsScreen(dao, inner)
                Tab.About -> AboutScreen(inner)
            }
        }
    }
}
