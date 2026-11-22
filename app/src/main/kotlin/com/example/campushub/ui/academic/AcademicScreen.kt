package com.example.campushub.ui.academic

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject

private const val INSTALL_HOOK_JS = """
(function() {
  if (window.__campusHookInstalled) return;
  window.__campusHookInstalled = true;
  window.__campusApiLog = window.__campusApiLog || [];
  function pushLog(method, rawUrl) {
    try {
      var u = new URL(rawUrl, location.href);
      if (u.hostname.indexOf('tongji.edu.cn') === -1) return;
      var entry = method.toUpperCase() + ' ' + u.pathname;
      if (window.__campusApiLog.indexOf(entry) === -1) {
        window.__campusApiLog.push(entry);
        if (window.__campusApiLog.length > 50) window.__campusApiLog.shift();
      }
    } catch (e) {}
  }
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function(input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var method = (init && init.method) || (input && input.method) || 'GET';
      pushLog(method, url);
      return origFetch.apply(this, arguments);
    };
  }
  var origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(method, url) {
    pushLog(method || 'GET', url || '');
    return origOpen.apply(this, arguments);
  };
})();
"""

private const val PROBE_DOM_JS = """
(function() {
  var tables = [];
  document.querySelectorAll('table').forEach(function(t) {
    var headers = [];
    t.querySelectorAll('th').forEach(function(th) {
      var txt = (th.innerText || '').trim();
      if (txt) headers.push(txt);
    });
    if (headers.length > 0) tables.push(headers.slice(0, 12));
  });
  var menus = [];
  document.querySelectorAll('.el-menu-item, .el-sub-menu__title, nav a, .menu-item').forEach(function(el) {
    var txt = (el.innerText || '').trim().replace(/\s+/g, ' ');
    if (txt && txt.length <= 24 && menus.indexOf(txt) === -1) menus.push(txt);
  });
  return JSON.stringify({
    title: document.title || '',
    route: location.pathname + (location.hash || ''),
    apis: window.__campusApiLog || [],
    tableHeaders: tables.slice(0, 5),
    menus: menus.slice(0, 20)
  });
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AcademicScreen(
  viewModel: AcademicViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var progress by remember { mutableIntStateOf(0) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var currentTitle by remember { mutableStateOf("同济教务 (1.tongji.edu.cn)") }
  var copiedHint by remember { mutableStateOf(false) }

  BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

  DisposableEffect(Unit) {
    onDispose { CookieManager.getInstance().flush() }
  }

  Column(modifier = modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack, modifier = Modifier.size(34.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退", modifier = Modifier.size(18.dp))
      }
      IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward, modifier = Modifier.size(34.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进", modifier = Modifier.size(18.dp))
      }
      IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
      }
      Text(
        text = currentTitle,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
      )
      FilledTonalButton(
        onClick = {
          copiedHint = false
          webViewRef?.evaluateJavascript(PROBE_DOM_JS) { rawResult ->
            val formatted = formatProbeResult(rawResult)
            viewModel.showProbeReport(formatted)
          }
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(30.dp),
      ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("探测结构", fontSize = 11.sp)
      }
      IconButton(onClick = { webViewRef?.loadUrl(uiState.homeUrl) }, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Default.Home, contentDescription = "教务首页", modifier = Modifier.size(18.dp))
      }
      IconButton(
        onClick = {
          val cm = CookieManager.getInstance()
          cm.removeAllCookies {
            cm.flush()
            webViewRef?.clearCache(true)
            webViewRef?.loadUrl(uiState.homeUrl)
          }
        },
        modifier = Modifier.size(34.dp),
      ) {
        Icon(Icons.Default.Delete, contentDescription = "清除登录态", modifier = Modifier.size(18.dp))
      }
    }

    if (progress in 1..99) {
      LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = Modifier.fillMaxWidth().height(2.dp),
      )
    }

    AndroidView(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      factory = { ctx ->
        WebView(ctx).apply {
          val cm = CookieManager.getInstance()
          cm.setAcceptCookie(true)
          cm.setAcceptThirdPartyCookies(this, true)

          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          settings.useWideViewPort = true
          settings.loadWithOverviewMode = true
          settings.setSupportZoom(true)
          settings.builtInZoomControls = true
          settings.displayZoomControls = false

          webViewClient =
            object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                currentTitle = view?.title?.ifBlank { "同济教务" } ?: "同济教务"
                view?.evaluateJavascript(INSTALL_HOOK_JS, null)
                CookieManager.getInstance().flush()
              }
            }

          webChromeClient =
            object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
              }

              override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) currentTitle = title
              }
            }

          webViewRef = this
          loadUrl(uiState.homeUrl)
        }
      },
      update = { webViewRef = it },
    )
  }

  val report = uiState.probeReport
  if (report != null) {
    AlertDialog(
      onDismissRequest = viewModel::dismissProbeReport,
      title = { Text("当前页面结构探测（已去敏）") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "此探测仅提取接口路径与表头结构（不含个人成绩/学号），可用于下一步替换成原生手机界面：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          SelectionContainer {
            Text(text = report, style = MaterialTheme.typography.bodySmall)
          }
          if (copiedHint) {
            Text(
              text = "已复制到剪贴板",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("tongji_probe", report))
            copiedHint = true
          }
        ) {
          Text("复制结果")
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::dismissProbeReport) { Text("关闭") }
      },
    )
  }
}

private fun formatProbeResult(rawJsReturn: String?): String {
  if (rawJsReturn.isNullOrBlank() || rawJsReturn == "null") return "未能读取当前页面结构，请等待页面加载完成后重试。"
  return runCatching {
    // WebView evaluateJavascript wraps JSON strings in quotes and escapes inner quotes.
    val unescaped =
      if (rawJsReturn.startsWith("\"") && rawJsReturn.endsWith("\"")) {
        JSONObject("{\"v\":$rawJsReturn}").getString("v")
      } else {
        rawJsReturn
      }
    val obj = JSONObject(unescaped)
    buildString {
      appendLine("标题: ${obj.optString("title")}")
      appendLine("路由: ${obj.optString("route")}")
      val apis = obj.optJSONArray("apis")
      appendLine("\n捕获到的同域 API (${apis?.length() ?: 0}):")
      if (apis != null) {
        for (i in 0 until apis.length()) appendLine("- ${apis.getString(i)}")
      }
      val tables = obj.optJSONArray("tableHeaders")
      appendLine("\n表格表头 (${tables?.length() ?: 0}):")
      if (tables != null) {
        for (i in 0 until tables.length()) appendLine("- ${tables.getJSONArray(i)}")
      }
      val menus = obj.optJSONArray("menus")
      appendLine("\n可见菜单项 (${menus?.length() ?: 0}):")
      if (menus != null) {
        for (i in 0 until menus.length()) appendLine("- ${menus.getString(i)}")
      }
    }.trim()
  }.getOrDefault(rawJsReturn)
}
