package com.example.campushub.ui.academic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AcademicUiState(
  val homeUrl: String = DEFAULT_URL,
  val probeReport: String? = null,
) {
  companion object {
    const val DEFAULT_URL = "https://1.tongji.edu.cn/workbench"
  }
}

class AcademicViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(AcademicUiState())
  val uiState: StateFlow<AcademicUiState> = _uiState.asStateFlow()

  fun showProbeReport(report: String) {
    _uiState.update { it.copy(probeReport = report) }
  }

  fun dismissProbeReport() {
    _uiState.update { it.copy(probeReport = null) }
  }
}
