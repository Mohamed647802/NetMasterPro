package com.netmaster.pro.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.netmaster.pro.presentation.home.HomeScreen
import com.netmaster.pro.presentation.signal.SignalScreen
import com.netmaster.pro.presentation.sms.SmsScreen
import com.netmaster.pro.presentation.devices.DevicesScreen
import com.netmaster.pro.presentation.wifi.WifiAdminScreen
import com.netmaster.pro.presentation.qos.QosScreen
import com.netmaster.pro.presentation.parental.ParentalScreen
import com.netmaster.pro.presentation.analyzer.WifiAnalyzerScreen
import com.netmaster.pro.presentation.speedtest.SpeedTestScreen
import com.netmaster.pro.presentation.quota.QuotaScreen
import com.netmaster.pro.presentation.security.SecurityAuditScreen
import com.netmaster.pro.presentation.troubleshooter.TroubleshooterScreen
import com.netmaster.pro.presentation.tools.NetworkToolsScreen
import com.netmaster.pro.presentation.sharing.QrCodeScreen
import com.netmaster.pro.presentation.sharing.OcrScannerScreen
import com.netmaster.pro.presentation.report.ReportScreen
import com.netmaster.pro.presentation.settings.SettingsScreen
import com.netmaster.pro.presentation.setup.RouterSetupScreen
import com.netmaster.pro.presentation.webview.RouterWebViewScreen
import com.netmaster.pro.presentation.dsl.DslAnalyzerScreen
import com.netmaster.pro.presentation.wifiheatmap.WifiHeatmapScreen

sealed class Screen(val route: String) {
    object Home          : Screen("home")
    object Signal        : Screen("signal")
    object Sms           : Screen("sms")
    object Devices       : Screen("devices")
    object WifiAdmin     : Screen("wifi_admin")
    object Qos           : Screen("qos")
    object Parental      : Screen("parental")
    object WifiAnalyzer  : Screen("wifi_analyzer")
    object WifiHeatmap   : Screen("wifi_heatmap")
    object SpeedTest     : Screen("speed_test")
    object Quota         : Screen("quota")
    object DslAnalyzer   : Screen("dsl_analyzer")
    object Security      : Screen("security")
    object Troubleshooter: Screen("troubleshooter")
    object NetworkTools  : Screen("network_tools")
    object QrCode        : Screen("qr_code")
    object OcrScanner    : Screen("ocr_scanner")
    object Report        : Screen("report")
    object Settings      : Screen("settings")
    object RouterSetup   : Screen("router_setup")
    object RouterWebView : Screen("router_webview")
}

@Composable
fun NetMasterNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route)          { HomeScreen(navController) }
        composable(Screen.Signal.route)        { SignalScreen() }
        composable(Screen.Sms.route)           { SmsScreen() }
        composable(Screen.Devices.route)       { DevicesScreen() }
        composable(Screen.WifiAdmin.route)     { WifiAdminScreen() }
        composable(Screen.Qos.route)           { QosScreen(navController) }
        composable(Screen.Parental.route)      { ParentalScreen() }
        composable(Screen.WifiAnalyzer.route)  { WifiAnalyzerScreen() }
        composable(Screen.WifiHeatmap.route)   { WifiHeatmapScreen(navController) }
        composable(Screen.SpeedTest.route)     { SpeedTestScreen() }
        composable(Screen.Quota.route)         { QuotaScreen() }
        composable(Screen.DslAnalyzer.route)   { DslAnalyzerScreen() }
        composable(Screen.Security.route)      { SecurityAuditScreen() }
        composable(Screen.Troubleshooter.route){ TroubleshooterScreen() }
        composable(Screen.NetworkTools.route)  { NetworkToolsScreen() }
        composable(Screen.QrCode.route)        { QrCodeScreen() }
        composable(Screen.OcrScanner.route)    { OcrScannerScreen() }
        composable(Screen.Report.route)        { ReportScreen() }
        composable(Screen.Settings.route)      { SettingsScreen(navController) }
        composable(Screen.RouterSetup.route)   { RouterSetupScreen(navController) }
        composable(Screen.RouterWebView.route) {
            RouterWebViewScreen(
                routerUrl = "http://192.168.1.1",
                onBack    = { navController.popBackStack() }
            )
        }
    }
}
