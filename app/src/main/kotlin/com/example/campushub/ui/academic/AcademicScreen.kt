package com.example.campushub.ui.academic

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
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

private const val INSTALL_HOOK_JS = """
(function() {
  if (window.__campusHookInstalled) return;
  window.__campusHookInstalled = true;
  window.__campusApiMap = window.__campusApiMap || {};

  function toSchema(val, depth) {
    if (depth > 4) return '...';
    if (val === null || val === undefined) return 'null';
    if (Array.isArray(val)) {
      return val.length === 0 ? [] : [toSchema(val[0], depth + 1)];
    }
    var t = typeof val;
    if (t === 'string') return '<string>';
    if (t === 'number') return '<number>';
    if (t === 'boolean') return '<boolean>';
    if (t === 'object') {
      var out = {};
      var keys = Object.keys(val).slice(0, 22);
      for (var i = 0; i < keys.length; i++) {
        out[keys[i]] = toSchema(val[keys[i]], depth + 1);
      }
      return out;
    }
    return '<' + t + '>';
  }

  function trySchema(raw) {
    if (!raw || typeof raw !== 'string') return null;
    try { return toSchema(JSON.parse(raw), 0); } catch (e) { return null; }
  }

  function recordApi(method, rawUrl, reqBodyRaw, respTextRaw) {
    try {
      var u = new URL(rawUrl, location.href);
      if (u.hostname.indexOf('tongji.edu.cn') === -1) return;
      if (u.pathname.indexOf('/api/') === -1) return;
      var m = (method || 'GET').toUpperCase();
      var key = m + ' ' + u.pathname;
      var qKeys = [];
      u.searchParams.forEach(function(_, k) {
        if (qKeys.indexOf(k) === -1) qKeys.push(k);
      });
      var prev = window.__campusApiMap[key] || {};
      window.__campusApiMap[key] = {
        method: m,
        path: u.pathname,
        queryKeys: qKeys.length > 0 ? qKeys : (prev.queryKeys || []),
        reqSchema: trySchema(reqBodyRaw) || prev.reqSchema || null,
        respSchema: trySchema(respTextRaw) || prev.respSchema || null
      };
    } catch (e) {}
  }

  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function(input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var method = (init && init.method) || (input && input.method) || 'GET';
      var reqBody = init && typeof init.body === 'string' ? init.body : null;
      return origFetch.apply(this, arguments).then(function(resp) {
        try {
          resp.clone().text().then(function(txt) {
            recordApi(method, url, reqBody, txt);
          }).catch(function() { recordApi(method, url, reqBody, null); });
        } catch (e) { recordApi(method, url, reqBody, null); }
        return resp;
      });
    };
  }

  var origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(method, url) {
    this.__campusMethod = method || 'GET';
    this.__campusUrl = url || '';
    return origOpen.apply(this, arguments);
  };
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function(body) {
    var self = this;
    var reqBody = typeof body === 'string' ? body : null;
    this.addEventListener('load', function() {
      var respText = null;
      try {
        if (self.responseType === '' || self.responseType === 'text') {
          respText = self.responseText;
        }
      } catch (e) {}
      recordApi(self.__campusMethod, self.__campusUrl, reqBody, respText);
    });
    return origSend.apply(this, arguments);
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
  document.querySelectorAll('.el-menu-item, .el-sub-menu__title, [role=menuitem], [role=tab], nav a, button, .el-card__header').forEach(function(el) {
    var txt = (el.innerText || '').trim().replace(/\s+/g, ' ');
    if (txt && txt.length >= 2 && txt.length <= 24 && menus.indexOf(txt) === -1) menus.push(txt);
  });
  var map = window.__campusApiMap || {};
  var apis = Object.keys(map).map(function(k) { return map[k]; });
  return JSON.stringify({
    title: document.title || '',
    route: location.pathname + (location.hash || ''),
    apis: apis,
    tableHeaders: tables.slice(0, 5),
    menus: menus.slice(0, 25)
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
            viewModel.showProbeReport(AcademicProbeFormatter.formatProbeJson(rawResult))
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
          settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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
                view?.evaluateJavascript(INSTALL_HOOK_JS, null)
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
      title = { Text("Schema 结构探测（已全量脱敏）") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "所有真实内容已替换为 <string>/<number> 类型占位符，不含个人数据：",
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
            clipboard.setPrimaryClip(ClipData.newPlainText("tongji_schema_probe", report))
            copiedHint = true
          }
        ) {
          Text("复制结果")
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              webViewRef?.evaluateJavascript("window.__campusApiMap = {};", null)
              viewModel.dismissProbeReport()
            }
          ) {
            Text("清空记录")
          }
          TextButton(onClick = viewModel::dismissProbeReport) { Text("关闭") }
        }
      },
    )
  }
}
