package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Sample data for one "nearby location" row, standing in for whatever real list content a caller
 * would put in [sheetContent] - the point of this screen is exercising [AppBottomSheetScaffold]'s
 * four states + drag + external control, not this fake data itself. */
private data class DemoPlace(val name: String, val distance: String)

private val DEMO_PLACES = listOf(
    DemoPlace("Central library", "0.4 km"),
    DemoPlace("Riverside cafe", "0.7 km"),
    DemoPlace("Old town market", "1.1 km"),
    DemoPlace("Botanical garden", "1.6 km"),
    DemoPlace("Harbor viewpoint", "2.0 km"),
    DemoPlace("City museum", "2.3 km"),
)

/**
 * Exercises every [AppBottomSheetValue] plus manual drag, per the component's acceptance
 * criteria - a real [NavigationBar] that must stay visible/interactive at every sheet state, a
 * scrollable list as sheet content, a title, a close button, and buttons to drive each state
 * programmatically alongside natural dragging.
 */
@Composable
fun BottomSheetDemoScreen() {
    val sheetState = rememberAppBottomSheetState(initialValue = AppBottomSheetValue.Collapsed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    AppBottomSheetScaffold(
        sheetState = sheetState,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.LocationOn, contentDescription = "Saved") },
                    label = { Text("Saved") },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "User") },
                    label = { Text("User") },
                )
            }
        },
        sheetContent = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = EvolaSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${DEMO_PLACES.size} nearby locations", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { scope.launch { sheetState.hide() } }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(EvolaSpacing.sm))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
                ) {
                    items(DEMO_PLACES) { place ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(place.name, style = MaterialTheme.typography.bodyLarge)
                                Text(place.distance, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
                Text("Map / main content", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Current sheet state: ${sheetState.currentValue}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { sheetState.hide() } }) { Text("Hide") }
                    OutlinedButton(onClick = { scope.launch { sheetState.collapse() } }) { Text("Collapse") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { sheetState.halfExpand() } }) { Text("Half expand") }
                    OutlinedButton(onClick = { scope.launch { sheetState.expand() } }) { Text("Expand") }
                }
            }
        }
    }
}

@Preview
@Composable
private fun BottomSheetDemoScreenPreview() {
    EvolaTheme {
        BottomSheetDemoScreen()
    }
}
