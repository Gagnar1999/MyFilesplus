package com.mfp.filemanager

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.mfp.filemanager.data.FileOperationManager
import com.mfp.filemanager.data.OperationStatus
import com.mfp.filemanager.data.OperationType
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.data.cache.AppCache
import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.data.clipboard.TransferStatus
import com.mfp.filemanager.databinding.ActivityMainBinding
import com.mfp.filemanager.security.PermissionHelper
import com.mfp.filemanager.ui.FileProgressController
import com.mfp.filemanager.ui.components.MiniPlayer
import com.mfp.filemanager.ui.viewmodels.AudioViewModel
import com.mfp.filemanager.ui.viewmodels.MainViewModel
import com.mfp.filemanager.utils.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val audioViewModel: AudioViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController
    private lateinit var fileProgressController: FileProgressController

    private var isSwipeNavEnabled = false
    private val themeHelper by lazy { ThemeHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsRepository = SettingsRepository(applicationContext)
        val themeMode = runBlocking { settingsRepository.themeMode.first() }
        isSwipeNavEnabled = runBlocking { settingsRepository.swipeNavigationEnabled.first() }
        themeHelper.setTheme(themeMode)

        super.onCreate(savedInstanceState)

        // Check Permissions
        try {
            if (!PermissionHelper.hasStoragePermission(this) ||
                !PermissionHelper.hasUsageStatsPermission(this)) {
                startActivity(android.content.Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }
        } catch (e: Exception) {
             e.printStackTrace()
             // Fallback to onboarding if check fails
             try {
                startActivity(android.content.Intent(this, OnboardingActivity::class.java))
                finish()
                return
             } catch (e2: Exception) {
                 e2.printStackTrace()
             }
        }
        
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            enableEdgeToEdge()
            setContentView(binding.root)

            ViewCompat.setOnApplyWindowInsetsListener(binding.root){v, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(insets.left, insets.top, insets.right, insets.bottom )
                WindowInsetsCompat.CONSUMED
            }


            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController

            val menuIds = listOf(R.id.nav_home, R.id.nav_music, R.id.nav_trash, R.id.nav_settings)
            val rootPagerId = R.id.nav_main_pager

            // Define Navigation Listener
            val navigationListener = com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener { item ->
                val currentId = navController.currentDestination?.id ?: -1
                if (currentId != rootPagerId) {
                     val popped = navController.popBackStack(rootPagerId, false)
                     if (!popped) {
                         navController.navigate(rootPagerId)
                     }
                }
                
                val index = menuIds.indexOf(item.itemId)
                if (index != -1) {
                    mainViewModel.requestTabChange(index)
                    return@OnItemSelectedListener true
                }
                false
            }
            
            binding.bottomNavigation.setOnItemSelectedListener(navigationListener)
            
            binding.bottomNavigation.setOnItemReselectedListener { item ->
                 val currentId = navController.currentDestination?.id ?: -1
                 if (currentId != rootPagerId) {
                     navController.popBackStack(rootPagerId, false)
                 }
                 val index = menuIds.indexOf(item.itemId)
                 if (index != -1) mainViewModel.requestTabChange(index)
            }

            // Sync UI when ViewPager swipes (ViewModel source of truth)
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    mainViewModel.currentTab.collect { index ->
                        if (index in menuIds.indices) {
                            val id = menuIds[index]
                            if (binding.bottomNavigation.selectedItemId != id) {
                                // Avoid loop: Temporarily remove listener to update UI only
                                binding.bottomNavigation.setOnItemSelectedListener(null)
                                binding.bottomNavigation.selectedItemId = id
                                binding.bottomNavigation.setOnItemSelectedListener(navigationListener)
                            }
                        }
                    }
                }
            }
            
            // Handle Back Button specifically for Pager logic
            onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (navController.currentDestination?.id == rootPagerId) {
                        if (mainViewModel.currentTab.value != 0) {
                            mainViewModel.requestTabChange(0)
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            })

            this.navController = navController

            setupMiniPlayer()
        } catch (e: Exception) {
            e.printStackTrace()
            // Vital crash: If UI fails to load, we can't do much. 
            // Try restart or finish to avoid stubborn black screen?
            // startActivity(android.content.Intent(this, OnboardingActivity::class.java)) // Maybe downgrade to onboarding?
            // For now, allow text trace.
            android.widget.Toast.makeText(this, "Error initializing UI: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        
        audioViewModel.initializeController(this)
        
        // Optimize Mini Player reveal animation
        val transition = android.animation.LayoutTransition()
        transition.setDuration(android.animation.LayoutTransition.APPEARING, 300)
        transition.setDuration(android.animation.LayoutTransition.DISAPPEARING, 300)
        transition.enableTransitionType(android.animation.LayoutTransition.CHANGING)
        binding.bottomBarContainer.layoutTransition = transition

        fileProgressController = FileProgressController(binding.fileProgressLayout) { isVisible ->
            if (isVisible) {
                 binding.bottomBarContainer.animate()
                    .translationY(binding.bottomBarContainer.height.toFloat().coerceAtLeast(200f))
                    .setDuration(300)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .start()
            } else {
                // Only show if we are not in player mode
                if (navController.currentDestination?.id != R.id.nav_player) {
                     binding.bottomBarContainer.animate()
                        .translationY(0f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
            }
        }

        // Global layout listener to detect keyboard visibility and hide the taskbar
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            // If keypadHeight is > 15% of screen height, keyboard is likely visible
            if (keypadHeight > screenHeight * 0.15) {
                if (binding.bottomBarContainer.visibility != View.GONE) {
                    binding.bottomBarContainer.visibility = View.GONE
                }
            } else {
                // Keyboard is hidden, restore taskbar if not in player mode
                val currentId = try { navController.currentDestination?.id } catch(e: Exception) { -1 }
                if (currentId != R.id.nav_player) {
                    if (binding.bottomBarContainer.visibility != View.VISIBLE) {
                        binding.bottomBarContainer.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupMiniPlayer() {
        val playerNavOptions = navOptions{
            anim {
                enter = R.anim.pop_enter
                exit = R.anim.pop_exit
                popEnter = R.anim.pop_pop_enter
                popExit = R.anim.pop_pop_exit
            }
        }

        binding.layoutMiniPlayer.setContent {
            val isPlaying by audioViewModel.isPlaying.collectAsStateWithLifecycle()
            val currentTrack by audioViewModel.currentTrack.collectAsStateWithLifecycle()
            val progress by audioViewModel.progress.collectAsStateWithLifecycle()
            Mdc3Theme() {
                MiniPlayer(
                    title = currentTrack?.title.toString(),
                    artist = currentTrack?.artist.toString(),
                    isPlaying = isPlaying,
                    progress = progress,
                    onPlayPause = {
                        audioViewModel.togglePlayPause()
                    },
                    onNext = {
                        audioViewModel.playNext()
                    },
                    onClose = {
                        audioViewModel.stopPlayer()
                    },
                    modifier = Modifier
                )
            }
        }


        // Set initial state to GONE to prevent flash on recreation
        binding.layoutMiniPlayer.visibility = View.GONE

        binding.layoutMiniPlayer.setOnClickListener {
            navController.navigate(R.id.nav_player, null, playerNavOptions)
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    audioViewModel.currentTrack.collect { metadata ->
                        // Only show if we have valid metadata (Title is mandatory)
                        if (metadata != null && !metadata.title.isNullOrBlank()) {
                            if (!binding.layoutMiniPlayer.isVisible) {
                                binding.layoutMiniPlayer.visibility = View.VISIBLE
                                // Start from behind the bottom navigation bar with fade and scale
                                val hideTranslation = 500f
                                binding.layoutMiniPlayer.translationY = hideTranslation
                                binding.layoutMiniPlayer.alpha = 0f
                                binding.layoutMiniPlayer.scaleX = 0.8f
                                binding.layoutMiniPlayer.scaleY = 0.8f
                                
                                binding.layoutMiniPlayer.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(300)
                                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                                    .start()
                            }
                        } else { // Metadata null (stopped or cleared)
                             if (binding.layoutMiniPlayer.isVisible) {
                                 val hideTranslation = 500f
                                 binding.layoutMiniPlayer.animate()
                                    .translationY(hideTranslation)
                                    .alpha(0f)
                                    .scaleX(0.8f)
                                    .scaleY(0.8f)
                                    .setDuration(250)
                                    .withEndAction { 
                                        binding.layoutMiniPlayer.visibility = View.GONE
                                        // Reset alpha/scale for layout preview or next show
                                        binding.layoutMiniPlayer.alpha = 1f
                                        binding.layoutMiniPlayer.scaleX = 1f
                                        binding.layoutMiniPlayer.scaleY = 1f
                                    }
                                    .start()
                             }
                        }
                    }
                }

                launch {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val isPlayer = entry.destination.id == R.id.nav_player
                        
                        if (isPlayer) {
                             // Hide entire dock (MiniPlayer + NavBar)
                            binding.bottomBarContainer.animate()
                                .translationY(binding.bottomBarContainer.height.toFloat().coerceAtLeast(200f))
                                .setDuration(350)
                                .withEndAction {
                                    binding.bottomBarContainer.visibility = View.GONE
                                }
                                .start()
                        } else {
                            // Restore Dock
                            binding.bottomBarContainer.visibility = View.VISIBLE
                            binding.bottomBarContainer.animate()
                                .translationY(0f)
                                .setDuration(350)
                                .setInterpolator(android.view.animation.DecelerateInterpolator())
                                .start()
                                
                            // Manage Taskbar Visibility based on Swipe Mode
                            binding.bottomNavContainer.visibility = if (isSwipeNavEnabled) View.GONE else View.VISIBLE
                        }
                    }
                }

                launch {
                    FileOperationManager.progress.collect { progress ->
                        val clipboard = FileOperationManager.clipboard.value
                        val transferStatus = progress?.status ?: TransferStatus.COMPLETED

                        val isRunning = transferStatus == TransferStatus.STARTING || transferStatus == TransferStatus.IN_PROGRESS
                        
                        val activeType = FileOperationManager.activeOperationType.value
                        val type = if (activeType != OperationType.NONE) {
                            activeType
                        } else {
                            when (clipboard?.operation) {
                                ClipboardOperation.COPY -> OperationType.COPY
                                ClipboardOperation.MOVE -> OperationType.MOVE
                                else -> OperationType.NONE
                            }
                        }


                        val p = if (progress != null && progress.totalBytes > 0) {
                            progress.transferredBytes.toFloat() / progress.totalBytes
                        } else {
                            0f
                        }

                        val status = OperationStatus(
                            isRunning = isRunning,
                            type = type,
                            progress = p,
                            processedCount = progress?.completedFiles ?: 0,
                            totalCount = progress?.totalFiles ?: 0
                        )

                        val finalStatus = if (transferStatus == TransferStatus.COMPLETED) {
                             status.copy(isRunning = false, progress = 1f)
                        } else {
                             status
                        }

                        if (::fileProgressController.isInitialized) {
                            fileProgressController.update(finalStatus)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppCache.clear()
    }
}
