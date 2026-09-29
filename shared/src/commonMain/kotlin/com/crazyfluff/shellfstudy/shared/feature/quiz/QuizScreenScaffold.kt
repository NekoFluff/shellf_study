package com.crazyfluff.shellfstudy.shared.feature.quiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.components.AbandonSessionMenuItem
import com.crazyfluff.shellfstudy.shared.designsystem.components.CompactTopBar
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SubjectSearchOverlay

/** The test tags [QuizScreenScaffold]'s chrome carries — each quiz screen keeps its own names. */
data class QuizScreenChromeTestTags(
    val backButton: String,
    val searchButton: String,
    val overflowMenu: String,
    val abandonMenuItem: String,
    val abandonConfirmButton: String
)

/**
 * The frame both quiz screens share: a top bar with back, search and a session menu ending in
 * "Abandon session" (with its confirmation), the subject search overlay, and a slot for the screen's
 * answer-details sheet.
 *
 * Everything sits in one Box so the sheet's handle overlays the true bottom of the screen and picks up
 * real navigation-bar insets, rather than being laid out under the system gesture area. The search
 * overlay is drawn last, above the sheet.
 *
 * @param canManageSession whether there is a committed session for the menu to act on.
 * @param extraMenuItems items above "Abandon session"; call the given function to close the menu.
 * @param detailSheet the answer-details sheet, told whether search is covering it.
 */
@Composable
fun QuizScreenScaffold(
    onBack: () -> Unit,
    canManageSession: Boolean,
    abandonDialogText: String,
    onAbandon: () -> Unit,
    searchUiState: SearchUiState,
    onSearchQueryChange: (String) -> Unit,
    testTags: QuizScreenChromeTestTags,
    extraMenuItems: @Composable ColumnScope.(closeMenu: () -> Unit) -> Unit = {},
    detailSheet: @Composable BoxScope.(isSearchActive: Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CompactTopBar(
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag(testTags.backButton)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag(testTags.searchButton)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        if (canManageSession) {
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.testTag(testTags.overflowMenu)
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    extraMenuItems { menuExpanded = false }
                                    AbandonSessionMenuItem(
                                        label = "Abandon session",
                                        testTag = testTags.abandonMenuItem,
                                        onClick = { menuExpanded = false; showAbandonConfirm = true }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            if (showAbandonConfirm) {
                ConfirmationDialog(
                    title = "Abandon this session?",
                    text = abandonDialogText,
                    confirmLabel = "Abandon",
                    onConfirm = { showAbandonConfirm = false; onAbandon() },
                    onDismiss = { showAbandonConfirm = false },
                    confirmButtonTestTag = testTags.abandonConfirmButton
                )
            }

            Column(modifier = Modifier.fillMaxSize().padding(innerPadding), content = content)
        }

        detailSheet(isSearchActive)

        SubjectSearchOverlay(
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            uiState = searchUiState,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
