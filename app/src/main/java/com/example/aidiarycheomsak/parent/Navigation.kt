package com.example.aidiarycheomsak.parent

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.aidiarycheomsak.parent.data.PreferenceHelper
import com.example.aidiarycheomsak.parent.ui.*

@Composable
fun MainNavigation(initialReportId: State<String?>) {
  val context = LocalContext.current
  val preferenceHelper = PreferenceHelper(context)

  val startDestination = ParentHome

  val backStack = rememberNavBackStack(startDestination)

  val reportId = initialReportId.value
  LaunchedEffect(reportId) {
      if (reportId != null) {
          backStack.add(ParentDetail(reportId))
      }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
        entry<ParentHome> {
            ParentHomeScreen(
                onNavigateToDetail = { rId ->
                    backStack.add(ParentDetail(rId))
                },
                onNavigateToSettings = {
                    backStack.add(Settings)
                },
                modifier = Modifier.safeDrawingPadding()
            )
        }
        entry<ParentDetail> { key ->
            ParentDetailScreen(
                reportId = key.reportId,
                onBack = {
                    backStack.removeLastOrNull()
                },
                modifier = Modifier.safeDrawingPadding()
            )
        }
        entry<Settings> {
            SettingsScreen(
                onBack = {
                    backStack.removeLastOrNull()
                },
                onRoleChanged = {
                    // Do nothing in parent-only app
                },
                modifier = Modifier.safeDrawingPadding()
            )
        }
    }
  )
}
