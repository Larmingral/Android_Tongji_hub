package com.example.campushub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campushub.data.canvas.CanvasApiClient
import com.example.campushub.data.canvas.CanvasConfigStore
import com.example.campushub.data.local.CalendarRepository
import com.example.campushub.data.mail.ImapMailClient
import com.example.campushub.data.mail.MailConfigStore
import com.example.campushub.ui.academic.AcademicScreen
import com.example.campushub.ui.academic.AcademicViewModel
import com.example.campushub.ui.calendar.CalendarScreen
import com.example.campushub.ui.calendar.CalendarViewModel
import com.example.campushub.ui.canvas.CanvasScreen
import com.example.campushub.ui.canvas.CanvasViewModel
import com.example.campushub.ui.mail.MailScreen
import com.example.campushub.ui.mail.MailViewModel
import com.example.campushub.ui.theme.CampusHubTheme

private enum class HubTab(val label: String, val icon: ImageVector) {
  CALENDAR("日历", Icons.Default.DateRange),
  CANVAS("Canvas", Icons.Default.Home),
  MAIL("邮件", Icons.Default.Email),
  ACADEMIC("教务", Icons.AutoMirrored.Filled.List),
}

class MainActivity : ComponentActivity() {

  private val calendarRepository by lazy { CalendarRepository(applicationContext) }
  private val canvasConfigStore by lazy { CanvasConfigStore(applicationContext) }
  private val canvasApiClient by lazy { CanvasApiClient() }
  private val mailConfigStore by lazy { MailConfigStore(applicationContext) }
  private val imapMailClient by lazy { ImapMailClient() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CampusHubTheme {
        CampusHubApp(
          calendarRepository = calendarRepository,
          canvasConfigStore = canvasConfigStore,
          canvasApiClient = canvasApiClient,
          mailConfigStore = mailConfigStore,
          imapMailClient = imapMailClient,
        )
      }
    }
  }
}

@Composable
private fun CampusHubApp(
  calendarRepository: CalendarRepository,
  canvasConfigStore: CanvasConfigStore,
  canvasApiClient: CanvasApiClient,
  mailConfigStore: MailConfigStore,
  imapMailClient: ImapMailClient,
) {
  var currentTab by rememberSaveable { mutableStateOf(HubTab.CALENDAR) }

  val calendarViewModel: CalendarViewModel =
    viewModel(factory = CalendarViewModel.Factory(calendarRepository))
  val canvasViewModel: CanvasViewModel =
    viewModel(
      factory = CanvasViewModel.Factory(canvasConfigStore, canvasApiClient, calendarRepository)
    )
  val mailViewModel: MailViewModel =
    viewModel(factory = MailViewModel.Factory(mailConfigStore, imapMailClient, calendarRepository))
  val academicViewModel: AcademicViewModel = viewModel()

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      NavigationBar {
        HubTab.entries.forEach { tab ->
          NavigationBarItem(
            selected = currentTab == tab,
            onClick = { currentTab = tab },
            icon = { Icon(tab.icon, contentDescription = tab.label) },
            label = { Text(tab.label) },
          )
        }
      }
    },
  ) { innerPadding ->
    when (currentTab) {
      HubTab.CALENDAR ->
        CalendarScreen(
          viewModel = calendarViewModel,
          modifier = Modifier.padding(innerPadding),
        )
      HubTab.CANVAS ->
        CanvasScreen(
          viewModel = canvasViewModel,
          modifier = Modifier.padding(innerPadding),
        )
      HubTab.MAIL ->
        MailScreen(
          viewModel = mailViewModel,
          modifier = Modifier.padding(innerPadding),
        )
      HubTab.ACADEMIC ->
        AcademicScreen(
          viewModel = academicViewModel,
          modifier = Modifier.padding(innerPadding),
        )
    }
  }
}
