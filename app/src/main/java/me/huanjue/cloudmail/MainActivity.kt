package me.huanjue.cloudmail

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.animation.doOnEnd
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.huanjue.cloudmail.data.api.NetworkModule
import me.huanjue.cloudmail.ui.VmFactory
import me.huanjue.cloudmail.ui.compose.ComposeScreen
import me.huanjue.cloudmail.ui.compose.ComposeViewModel
import me.huanjue.cloudmail.ui.home.HomeScreen
import me.huanjue.cloudmail.ui.login.LoginScreen
import me.huanjue.cloudmail.ui.login.LoginViewModel
import me.huanjue.cloudmail.ui.mail.DetailScreen
import me.huanjue.cloudmail.ui.settings.PgpKeyScreen
import me.huanjue.cloudmail.ui.settings.SettingsScreen
import me.huanjue.cloudmail.ui.theme.CloudMailTheme

object Routes {
    const val LOGIN = "login?addMode={addMode}"
    const val HOME = "home"
    const val DETAIL = "detail/{accountId}/{emailId}/{type}"
    const val COMPOSE = "compose?draftId={draftId}"
    const val SETTINGS = "settings"
    const val PGP_KEY = "pgp_key"

    fun login(addMode: Boolean = false) = "login?addMode=$addMode"

    fun detail(accountId: Long, emailId: Long, type: Int) =
        "detail/$accountId/$emailId/$type"

    fun compose(draftId: String? = null) =
        if (draftId == null) "compose" else "compose?draftId=$draftId"
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val lang = try {
            newBase.getSharedPreferences("cloudmail_prefs_sync", Context.MODE_PRIVATE)
                .getString("language", "system") ?: "system"
        } catch (_: Exception) { "system" }
        super.attachBaseContext(me.huanjue.cloudmail.ui.theme.LocaleHelper.wrap(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // 系统开屏直接做圆扩散转场：0.6s 停留后从中心向外扩散 0.5s 揭开主界面
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val cx = splashScreenView.width / 2f
            val cy = splashScreenView.height / 2f
            val finalRadius = kotlin.math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
            val reveal = android.view.ViewAnimationUtils.createCircularReveal(
                splashScreenView, cx.toInt(), cy.toInt(), 0f, finalRadius
            ).apply {
                duration = 500
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                doOnEnd { splashScreenView.remove() }
            }
            // 停留 0.6s 后开始扩散
            splashScreenView.postDelayed({ reveal.start() }, 600)
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 401（token 失效）时清掉本地会话并踢回登录页
        NetworkModule.onUnauthorized = {
            // 注意：此时可能在任意界面，用全局导航处理
            UnauthorizedBus.emit()
        }

        setContent {
            val app = applicationContext as CloudMailApp
            val themeMode by app.container.settings.themeMode.collectAsState(initial = "system")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val dynamicColor by app.container.settings.dynamicColor.collectAsState(initial = false)
            CloudMailTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                AppNav()
            }
        }
    }
}

/** 简单的全局 401 事件总线 */
object UnauthorizedBus {
    private val listeners = mutableSetOf<() -> Unit>()
    fun emit() = listeners.toList().forEach { it() }
    fun subscribe(listener: () -> Unit) { listeners.add(listener) }
    fun unsubscribe(listener: () -> Unit) { listeners.remove(listener) }
}

@Composable
fun AppNav() {
    val context = LocalContext.current
    val app = context.applicationContext as CloudMailApp
    val container = app.container
    val navController = rememberNavController()

    var startDestination by remember { mutableStateOf<String?>(null) }

    // 启动时恢复会话：有 token 直接进主页
    LaunchedEffect(Unit) {
        val ok = container.authRepository.restoreSession()
        startDestination = if (ok) Routes.HOME else Routes.login()
    }

    // 401：当前账号 token 失效时删掉该会话；还有别的账号就自动切过去，没有才踢回登录页
    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            MainScope().launch {
                val hasMore = container.authRepository.logout()
                if (!hasMore) {
                    navController.navigate(Routes.login()) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
        UnauthorizedBus.subscribe(listener)
        onDispose { UnauthorizedBus.unsubscribe(listener) }
    }

    if (startDestination == null) {
        // 启动闪屏
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = navController, startDestination = startDestination!!) {
        composable(
            route = Routes.LOGIN,
            arguments = listOf(
                navArgument("addMode") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            val addMode = entry.arguments?.getBoolean("addMode") == true
            val vm: LoginViewModel = viewModel(
                key = "login_$addMode",
                factory = VmFactory {
                    LoginViewModel(container.authRepository, container.settings)
                }
            )
            val capabilities by container.capabilitiesRepository.capabilities.collectAsState()
            LoginScreen(
                viewModel = vm,
                isAddMode = addMode,
                passkeySupported = capabilities.passkey,
                onLoginSuccess = {
                    if (addMode) {
                        // 添加账号：直接返回主页（新账号已自动设为活动）
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.login()) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HOME) { entry ->
            val sentRefreshNonce by entry.savedStateHandle
                .getStateFlow("refresh_sent_nonce", 0L)
                .collectAsState()
            HomeScreen(
                onOpenEmail = { accountId, emailId, type ->
                    navController.navigate(Routes.detail(accountId, emailId, type))
                },
                onCompose = { draftId -> navController.navigate(Routes.compose(draftId)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onAddAccount = { navController.navigate(Routes.login(addMode = true)) },
                sentRefreshNonce = sentRefreshNonce
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType },
                navArgument("emailId") { type = NavType.LongType },
                navArgument("type") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            DetailScreen(
                accountId = backStackEntry.arguments?.getLong("accountId") ?: 0L,
                emailId = backStackEntry.arguments?.getLong("emailId") ?: 0L,
                type = backStackEntry.arguments?.getInt("type") ?: 0,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.COMPOSE,
            arguments = listOf(
                navArgument("draftId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val draftId = backStackEntry.arguments?.getString("draftId")
            val vm: ComposeViewModel = viewModel(
                key = "compose_$draftId",
                factory = VmFactory {
                    ComposeViewModel(
                        container.mailRepository,
                        container.draftRepository,
                        container.settings,
                        draftId
                    )
                }
            )
            ComposeScreen(
                viewModel = vm,
                onSent = {
                    // 通知主页刷新已发送
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("refresh_sent_nonce", System.currentTimeMillis())
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.login()) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onPgpKey = { navController.navigate(Routes.PGP_KEY) }
            )
        }
        composable(Routes.PGP_KEY) {
            PgpKeyScreen(onBack = { navController.popBackStack() })
        }
    }
}
