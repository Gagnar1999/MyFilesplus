package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.databinding.FragmentMusicBinding
import com.mfp.filemanager.ui.adapters.FileListAdapter
import com.mfp.filemanager.ui.viewmodels.AudioViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import com.mfp.filemanager.R
import com.mfp.filemanager.ui.components.FileItem
import java.io.File
import kotlinx.coroutines.launch

class MusicFragment : Fragment() {

    override fun onCreateAnimation(
        transit: Int, enter: Boolean, nextAnim: Int
    ): android.view.animation.Animation? {
        // Force animation based on Main Activity's calculated direction
        val isMovingRight =
            com.mfp.filemanager.data.cache.AppCache.getData<Boolean>("isMovingRight") ?: true
        val animRes = if (enter) {
            if (isMovingRight) R.anim.slide_in_right else R.anim.slide_in_left
        } else {
            if (isMovingRight) R.anim.slide_out_left else R.anim.slide_out_right
        }
        return android.view.animation.AnimationUtils.loadAnimation(context, animRes)
    }

    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            requireActivity().application,
            FileRepository(requireContext().applicationContext),
            SettingsRepository(requireContext().applicationContext)
        )
    }

    private val audioViewModel: AudioViewModel by activityViewModels()

    private lateinit var musicAdapter: FileListAdapter

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = content {
        Mdc3Theme() {
            val files by viewModel.categoryFiles.collectAsStateWithLifecycle()

            Scaffold(
                topBar = { CenterAlignedTopAppBar(title = { Text("Music Library") }) }) { padding ->

                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(files, key = {file -> file.id}) { file ->
                        FileItem(
                            file.name,
                            file.dateModified.toString(),
                            Icons.Default.MusicNote,
                            file,
                            onClick = { file ->
                                audioViewModel.playFile(File(file.path))
                            }
                        )
                    }
                }

            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadFilesByCategory(FileType.AUDIO)
        audioViewModel.loadMusicFiles()
    }
}
