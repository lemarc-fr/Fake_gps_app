package com.lemarc.fake_gps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.data.geocoding.Place

/**
 * Barre de recherche d'adresse ; les suggestions s'affichent dessous quand elle est dépliée.
 * Valider au clavier choisit la première suggestion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    search: MainViewModel.SearchState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = {
                    onQueryChange(it)
                    onExpandedChange(true)
                },
                onSearch = { search.results.firstOrNull()?.let(onSelect) },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (search.isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear))
                            }
                        }
                    }
                },
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        SearchResults(search, onSelect)
    }
}

@Composable
private fun ColumnScope.SearchResults(search: MainViewModel.SearchState, onSelect: (Place) -> Unit) {
    val message = when {
        search.error != null ->
            stringResource(R.string.search_error, ErrorMessages.describe(LocalResources.current, search.error))
        search.noResult -> stringResource(R.string.search_no_result)
        else -> null
    }
    if (message != null) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    search.results.forEach { place ->
        ListItem(
            headlineContent = { Text(place.name, maxLines = 1) },
            supportingContent = place.detail.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onSelect(place) },
        )
    }
}
