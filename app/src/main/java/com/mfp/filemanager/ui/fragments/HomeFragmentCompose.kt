package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridTrackSize.Companion.Percentage
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.columns
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.compose.content
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.MediaItem
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.ui.activities.MediaViewerActivity
import com.mfp.filemanager.ui.adapters.FileListAdapter
import com.mfp.filemanager.ui.adapters.RecentFilesAdapter
import com.mfp.filemanager.ui.adapters.RecentsListItem
import com.mfp.filemanager.ui.components.FileItem
import com.mfp.filemanager.ui.components.GridItem
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import com.mfp.filemanager.utils.toReadableDate

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
            Scaffold { innerPadding ->
                HomeContent(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
            }

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
//        binding.swipeRefresh.isRefreshing = false
        viewModel.checkUsageAccess()
        viewModel.loadRecentFiles()
        viewModel.loadStorageInfo()
    }


    private fun refresh() {
        viewModel.checkUsageAccess()
        viewModel.loadRecentFiles()
        viewModel.loadStorageInfo()
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

        searchAdapter = FileListAdapter(
            onItemClick = { file ->

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

    private fun navigateToAllRecentFiles() {
        findNavController().navigate(R.id.nav_recent_files)
    }

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
    private fun HomeContent(modifier: Modifier = Modifier, viewModel: HomeViewModel) {
        val categories by viewModel.categories.collectAsStateWithLifecycle()
        val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
        val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()

        PullToRefreshBox(onRefresh = viewModel::refreshHomeData, isRefreshing = isRefreshing) {
            LazyColumn(
                modifier = modifier
                    .fillMaxWidth()
                    .fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Home",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Grid(
                        config = {
                            columns(Percentage(0.5f), Percentage(0.5f))
                            rowGap(18.dp)
                            columnGap(12.dp)
                        }, modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        repeat(categories.size) { pos ->
                            GridItem(categories[pos])
                        }
                    }
                }

                stickyHeader {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp)
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Files",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp)
                        )

                        TextButton(
                            onClick = ::navigateToAllRecentFiles
                        ) {
                            Text("View All")
                        }
                    }
                }

                items(recentFiles) {
                    FileItem(
                        it.name, it.date.toReadableDate(), icon = if (it.isDirectory)
                            Icons.Rounded.Folder
                        else
                            Icons.Rounded.Description,
                        it,
                        onClick = ::onFileItemClick
                    )
                }


            }
        }

    }

    private fun onFileItemClick(file: FileModel) {
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
    }
}