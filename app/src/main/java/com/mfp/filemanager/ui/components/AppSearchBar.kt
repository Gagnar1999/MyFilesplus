package com.mfp.filemanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    active: Boolean = false,
    onActiveChange: (Boolean) -> Unit = {},
    searchResults : List<FileModel>
) {
    SearchBar (
        modifier = modifier.fillMaxWidth(),
        expanded = active,
        onExpandedChange = onActiveChange,
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {
                    onSearch(it)
                    onActiveChange(false)
                },
                expanded = active,
                onExpandedChange = onActiveChange,
                placeholder = {
                    Text(placeholder)
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onQueryChange("")
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear"
                            )
                        }
                    }
                }
            )
        }
    ) {
        LazyColumn () {
            items(searchResults){
                ListItem(headlineContent = {
                    Text(it.name)
                })
            }
        }
    }
}