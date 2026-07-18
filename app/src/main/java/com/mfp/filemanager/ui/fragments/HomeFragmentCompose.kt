package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridFlow
import androidx.compose.foundation.layout.GridTrackSize.Companion.Percentage
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.columns
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.mfp.filemanager.R
import com.mfp.filemanager.data.CategoryItem
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.MediaItem
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.data.StorageInfo
import com.mfp.filemanager.databinding.FragmentHomeBinding
import com.mfp.filemanager.ui.activities.MediaViewerActivity
import com.mfp.filemanager.ui.adapters.FileListAdapter
import com.mfp.filemanager.ui.adapters.RecentFilesAdapter
import com.mfp.filemanager.ui.adapters.RecentsListItem
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import kotlinx.coroutines.launch
import kotlin.getValue

class HomeFragmentCompose : Fragment() {

    override fun onCreateAnimation(
        transit: Int,
        enter: Boolean,
        nextAnim: Int
    ): android.view.animation.Animation? {
        val isMovingRight =
            com.mfp.filemanager.data.cache.AppCache.getData<Boolean>("isMovingRight") ?: true

        val animRes = if (enter) {
            if (isMovingRight) R.anim.slide_in_right else R.anim.slide_in_left
        } else {
            if (isMovingRight) R.anim.slide_out_left else R.anim.slide_out_right
        }
        return android.view.animation.AnimationUtils.loadAnimation(context, animRes)
    }

    private val viewModel: HomeViewModel by activityViewModels {
        HomeViewModelFactory(
            requireActivity().application,
            FileRepository(requireContext().applicationContext),
            SettingsRepository(requireContext().applicationContext)
        )
    }

    private var isStorageAnimating = false
    private lateinit var recentFilesAdapter: RecentFilesAdapter
    private lateinit var searchAdapter: FileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        Mdc3Theme {
            HomeContent(viewModel = viewModel)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        setupSwipeRefresh()
//        binding.swipeRefresh.isRefreshing = false
        setupBackHandling()

        viewModel.checkUsageAccess()
        viewModel.loadRecentFiles()
        viewModel.loadStorageInfo()
    }

    override fun onResume() {
        super.onResume()
//        binding.swipeRefresh.isEnabled = true
//        binding.swipeRefresh.isRefreshing = false
    }

    private fun setupSwipeRefresh() {
        /*binding.swipeRefresh.setOnRefreshListener {
            viewModel.checkUsageAccess()
            viewModel.loadRecentFiles()
            viewModel.loadStorageInfo()

            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(2000)
                if (_binding != null && binding.swipeRefresh.isRefreshing) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }*/
    }

    private fun setupRecyclerView() {
        recentFilesAdapter = RecentFilesAdapter(
            onItemClick = { file ->
                if (viewModel.isRecentSelectionMode.value) {
                    viewModel.toggleRecentSelection(file)
                } else {
                    if (file.type == com.mfp.filemanager.data.FileType.IMAGE || file.type == com.mfp.filemanager.data.FileType.VIDEO) {
                        val mediaFiles = recentFilesAdapter.currentList.mapNotNull {
                            if (it is RecentsListItem.FileItem) it.file else null
                        }.filter {
                            it.type == com.mfp.filemanager.data.FileType.IMAGE || it.type == com.mfp.filemanager.data.FileType.VIDEO
                        }

                        val mediaItems = ArrayList<MediaItem>()
                        var startIndex = 0

                        mediaFiles.forEachIndexed { index, f ->
                            val uri = android.net.Uri.fromFile(java.io.File(f.path))
                            if (f.type == com.mfp.filemanager.data.FileType.IMAGE) {
                                mediaItems.add(MediaItem.Image(uri, f.name))
                            } else {
                                mediaItems.add(MediaItem.Video(uri, f.name))
                            }
                            if (f.path == file.path) startIndex = index
                        }

                        val intent =
                            android.content.Intent(
                                requireContext(),
                                MediaViewerActivity::class.java
                            ).apply {
                                putParcelableArrayListExtra("media_items", mediaItems)
                                putExtra("start_index", startIndex)
                            }
                        startActivity(intent)
                    } else {
                        FileUtils.openFile(requireContext(), file)
                    }
                }
            },
            onLongClick = { file ->
                viewModel.toggleRecentSelection(file)
            },
            isSelectionModeActive = { viewModel.isRecentSelectionMode.value }
        )
        /*binding.recyclerRecentFiles.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            adapter = recentFilesAdapter
        }*/

        searchAdapter = FileListAdapter(
            onItemClick = { file ->
                if (file.isDirectory) {
                    val bundle = Bundle().apply {
                        putString("path", file.path)
                    }
                    val navOptions = androidx.navigation.NavOptions.Builder()
                        .setEnterAnim(R.anim.pop_enter)
                        .setExitAnim(R.anim.pop_exit)
                        .setPopEnterAnim(R.anim.pop_pop_enter)
                        .setPopExitAnim(R.anim.pop_pop_exit)
                        .build()
                    findNavController().navigate(R.id.nav_file_browser, bundle, navOptions)
                } else {
                    if (file.type == com.mfp.filemanager.data.FileType.VIDEO || file.type == com.mfp.filemanager.data.FileType.IMAGE) {
                        val mediaFiles = searchAdapter.currentList.filter {
                            it.type == com.mfp.filemanager.data.FileType.IMAGE || it.type == com.mfp.filemanager.data.FileType.VIDEO
                        }

                        val mediaItems = ArrayList<MediaItem>()
                        var startIndex = 0

                        mediaFiles.forEachIndexed { index, f ->
                            val uri = android.net.Uri.fromFile(java.io.File(f.path))
                            if (f.type == com.mfp.filemanager.data.FileType.IMAGE) {
                                mediaItems.add(MediaItem.Image(uri, f.name))
                            } else {
                                mediaItems.add(MediaItem.Video(uri, f.name))
                            }
                            if (f.path == file.path) startIndex = index
                        }

                        val intent =
                            android.content.Intent(
                                requireContext(),
                                MediaViewerActivity::class.java
                            ).apply {
                                putParcelableArrayListExtra("media_items", mediaItems)
                                putExtra("start_index", startIndex)
                            }
                        startActivity(intent)
                    } else {
                        FileUtils.openFile(requireContext(), file)
                    }
                }
            },
            onLongClick = { /* No-op for search */ },
            onActionClick = { file, action ->
                handleFileAction(file, action)
            }
        )
        /* binding.recyclerSearchResults.apply {
             layoutManager = LinearLayoutManager(requireContext())
             adapter = searchAdapter
         }*/
    }

    private fun setupObservers() {
//        viewLifecycleOwner.lifecycleScope.launch {
//            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
//
//                launch {
//                    viewModel.storageInfo.collect { info ->
//                        val used = Formatter.formatFileSize(requireContext(), info.usedBytes)
//                        val totalStr = Formatter.formatFileSize(requireContext(), info.totalBytes)
//
//                        binding.textStorageUsageValue.text =
//                            getString(R.string.storage_usage_format, used, totalStr)
//
//                        val percent = if (info.totalBytes > 0) {
//                            (info.usedBytes.toDouble() / info.totalBytes * 100).toInt()
//                        } else 0
//
//                        if (info.totalBytes > 0) {
//                            binding.layoutProgressSegments.weightSum = 1.0f
//                            val total = info.totalBytes.toFloat()
//
//                            (binding.segmentVideos.layoutParams as LinearLayout.LayoutParams).weight =
//                                info.videoBytes / total
//                            (binding.segmentImages.layoutParams as LinearLayout.LayoutParams).weight =
//                                info.imageBytes / total
//                            (binding.segmentApps.layoutParams as LinearLayout.LayoutParams).weight =
//                                info.appBytes / total
//                            (binding.segmentDocs.layoutParams as LinearLayout.LayoutParams).weight =
//                                info.documentBytes / total
//                            (binding.segmentAudio.layoutParams as LinearLayout.LayoutParams).weight =
//                                info.audioBytes / total
//                            (binding.segmentOthers.layoutParams as LinearLayout.LayoutParams).weight =
//                                (info.otherBytes + info.archiveBytes) / total
//
//                            (binding.segmentOthers.layoutParams as LinearLayout.LayoutParams).weight =
//                                (info.otherBytes + info.archiveBytes) / total
//
//                            binding.layoutProgressSegments.requestLayout()
//                            binding.segmentFree.visibility = View.GONE
//
//                            // Ensure Container (Free Space Background) is always visible
//                            binding.containerProgress.setCardBackgroundColor(android.graphics.Color.WHITE)
//                            binding.viewProgressMask.visibility = View.GONE
//                        }
//
//                        if (!viewModel.hasStorageAnimated && info.totalBytes > 0) {
//                            // Hide ONLY the colored segments initially, but keep container visible
//                            binding.layoutProgressSegments.visibility = View.INVISIBLE
//                            binding.layoutProgressSegments.scaleX = 0f
//                            binding.layoutProgressSegments.alpha = 0f
//                            binding.textStoragePercent.text =
//                                getString(R.string.storage_percent_format, 0)
//
//                            isStorageAnimating = true
//
//                            viewLifecycleOwner.lifecycleScope.launch {
//                                kotlinx.coroutines.delay(500)
//                                if (_binding != null) {
//                                    animateStorageProgress(percent)
//                                }
//                            }
//                        } else if (info.totalBytes > 0) {
//                            binding.layoutProgressSegments.scaleX = 1f
//                            binding.layoutProgressSegments.alpha = 1f
//                            binding.layoutProgressSegments.visibility = View.VISIBLE
//                            binding.containerProgress.setCardBackgroundColor(android.graphics.Color.WHITE)
//                            binding.textStoragePercent.text =
//                                getString(R.string.storage_percent_format, percent)
//                        }
//                    }
//                }
//
//                launch {
//                    viewModel.recentFiles.collect { files ->
//                        val items = files.take(7).map { RecentsListItem.FileItem(it) }
//                        recentFilesAdapter.submitList(items)
//                    }
//                }
//
//                launch {
//                    kotlinx.coroutines.flow.combine(
//                        viewModel.searchResults,
//                        viewModel.searchQuery
//                    ) { results, query -> results to query }.collect { (results, query) ->
//                        searchAdapter.submitList(results)
//
//                        val isSearching = query.isNotEmpty()
//                        val hasResults = results.isNotEmpty()
//
//                        // Overlay visibility
//                        binding.viewBlurOverlay.visibility = if (isSearching) View.VISIBLE else View.GONE
//                        binding.recyclerSearchResults.visibility = if (isSearching && hasResults) View.VISIBLE else View.GONE
//
//                        // Empty state visibility
//                        binding.textSearchEmpty.visibility = if (isSearching && !hasResults) View.VISIBLE else View.GONE
//
//                        // Dashboard visibility
//                        binding.swipeRefresh.visibility = if (isSearching) View.GONE else View.VISIBLE
//                    }
//                }
//
//                launch {
//                    viewModel.isRecentSelectionMode.collect { _ ->
//                        recentFilesAdapter.notifyItemRangeChanged(0, recentFilesAdapter.itemCount)
//                    }
//                }
//
//                launch {
//                    viewModel.userMessage.collect { msg ->
//                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
//                    }
//                }
//
//                launch {
//                    com.mfp.filemanager.data.cache.AppCache.cacheClearEvents.collect {
//                        // Force refresh UI components when cache is cleared
//                        recentFilesAdapter.notifyDataSetChanged()
//                        searchAdapter.notifyDataSetChanged()
//
//                        // Reload data to ensure everything is in sync
//                        viewModel.loadRecentFiles()
//                        viewModel.loadStorageInfo()
//
//                        // Clear memory cache again for this context just in case
//                        coil.Coil.imageLoader(requireContext()).memoryCache?.clear()
//                    }
//                }
//
//                launch {
//                    viewModel.thumbnailSeed.collect { seed ->
//                        recentFilesAdapter.thumbnailSeed = seed
//                        searchAdapter.thumbnailSeed = seed
//                    }
//                }
//            }
//        }
    }

    private fun animateStorageProgress(targetPercent: Int) {
//        if (_binding == null) return
//
//        // Fix: Set "Free Space" background explicitly
//        binding.containerProgress.setCardBackgroundColor(android.graphics.Color.WHITE)
//        binding.viewProgressMask.visibility = View.GONE
//
//        binding.layoutProgressSegments.visibility = View.VISIBLE
//        binding.layoutProgressSegments.pivotX = 0f
//
//        // Custom Easing for 3.5s smooth load
//        val easeOutQuart = android.animation.TimeInterpolator { input ->
//            val t = 1f - input
//            1f - (t * t * t * t)
//        }
//
//        // Dual Animation: Scale & Alpha
//        val scaleAnim =
//            android.animation.ObjectAnimator.ofFloat(binding.layoutProgressSegments, "scaleX", 0f, 1f)
//        val alphaAnim =
//            android.animation.ObjectAnimator.ofFloat(binding.layoutProgressSegments, "alpha", 0f, 1f)
//
//        val set = android.animation.AnimatorSet()
//        set.playTogether(scaleAnim, alphaAnim)
//        set.duration = 3500
//        set.interpolator = easeOutQuart
//        set.start()
//
//        // Text counting animation
//        val animator = android.animation.ValueAnimator.ofInt(0, targetPercent)
//        animator.duration = 3500
//        animator.interpolator = easeOutQuart
//        animator.addUpdateListener { animation ->
//            if (_binding != null) {
//                val value = animation.animatedValue as Int
//                binding.textStoragePercent.text =
//                    getString(R.string.storage_percent_format, value)
//            }
//        }
//        animator.start()
//
//        viewModel.hasStorageAnimated = true
//        isStorageAnimating = false
    }

    private fun setupListeners() {
//        binding.inputSearch.addTextChangedListener(object : android.text.TextWatcher {
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                val query = s?.toString() ?: ""
//                viewModel.updateSearchQuery(query)
//
//                binding.btnSearchClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
//                binding.btnSearchBack.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
//                binding.imgSearchIcon.visibility = if (query.isEmpty()) View.VISIBLE else View.GONE
//            }
//
//            override fun afterTextChanged(s: android.text.Editable?) {}
//        })
//
//        binding.btnSearchBack.setOnClickListener {
//            binding.inputSearch.setText("")
//            viewModel.clearSearch()
//            hideKeyboard()
//        }
//
//        binding.btnSearchClear.setOnClickListener {
//            binding.inputSearch.setText("")
//            viewModel.clearSearch()
//        }
//
//        binding.cardBrowseInternal.setOnClickListener {
//            val bundle = Bundle().apply {
//                putString("path", android.os.Environment.getExternalStorageDirectory().absolutePath)
//            }
//            findNavController().navigate(R.id.nav_file_browser, bundle)
//        }
//
//        binding.btnViewRecentAll.setOnClickListener {
//            findNavController().navigate(R.id.nav_recent_files)
//        }
//
//        binding.cardForecast.setOnClickListener {
//            findNavController().navigate(R.id.nav_storage_forecast)
//        }
    }

    private fun setupBackHandling() {
        /* requireActivity().onBackPressedDispatcher.addCallback(
             viewLifecycleOwner,
             object : OnBackPressedCallback(true) {
                 override fun handleOnBackPressed() {
                     if (viewModel.searchQuery.value.isNotEmpty()) {
                         binding.inputSearch.setText("")
                         viewModel.clearSearch()
                         hideKeyboard()
                     } else if (viewModel.isRecentSelectionMode.value) {
                         viewModel.exitRecentSelectionMode()
                     } else {
                         isEnabled = false
                         requireActivity().onBackPressedDispatcher.onBackPressed()
                     }
                 }
             })*/
    }

    /* private fun hideKeyboard() {
         val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
         imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
         binding.inputSearch.clearFocus()
     }*/

    private fun handleFileAction(file: FileModel, action: String) {
        when (action) {
            "Open" -> FileUtils.openFile(requireContext(), file)
            "Delete" -> viewModel.deleteFile(file.path)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    @OptIn(ExperimentalGridApi::class)
    @Composable
    private fun HomeContent(modifier: Modifier = Modifier, viewModel : HomeViewModel) {
        val categories by viewModel.categories.collectAsStateWithLifecycle()

        Column(modifier = modifier.fillMaxWidth()) {
            Text(
                "Home",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            //Search Bar

            Grid(
                config = {
                    columns(Percentage(0.5f), Percentage(0.5f))
                    rowGap(18.dp)
                    columnGap(12.dp)
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                repeat(categories.size) { pos ->
                    GridItem(categories[pos])
                }
            }

        }
    }


    @Composable
    private fun GridItem(
        category: CategoryItem,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val animatedProgress by animateFloatAsState(
            targetValue = category.progress,
            animationSpec = tween(
                durationMillis = 700,
                easing = LinearEasing
            ),
            label = "storage_progress"
        )


        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable {
                    // Open selected category
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme
                            .colorScheme
                            .primaryContainer
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(category.id),
                            contentDescription = null,
                            modifier = Modifier.padding(6.dp),
                            tint = MaterialTheme
                                .colorScheme
                                .onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = category.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }


                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                // Storage percentage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text = "Storage used",
                        fontSize = 12.sp,
                        color = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    )

                    Text(
                        text = category.storageUsedReadable,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme
                            .colorScheme
                            .primary
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                // Storage progress indicator
                LinearProgressIndicator(
                    progress = {
                        animatedProgress
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(
                            RoundedCornerShape(50)
                        ),
                    color = MaterialTheme
                        .colorScheme
                        .primary,
                    trackColor = MaterialTheme
                        .colorScheme
                        .surfaceVariant
                )
            }
        }
    }

    private fun getCategoryIcon(
        categoryId: String
    ): ImageVector {
        return when (categoryId) {
            "1" -> Icons.Default.VideoLibrary
            "2" -> Icons.Default.Image
            "3" -> Icons.Default.LibraryBooks
            "4" -> Icons.Default.DocumentScanner
            else -> Icons.Default.InsertDriveFile
        }
    }
}