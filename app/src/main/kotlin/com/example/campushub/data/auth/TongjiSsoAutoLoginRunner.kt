package com.example.campushub.data.auth

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URI
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface SsoRefreshResult {
  data object Success : SsoRefreshResult

  data class Failed(
    val reason: String,
    val requiresInteractiveLogin: Boolean = false,
  ) : SsoRefreshResult
}

object TongjiSsoAutoLoginRunner {

  private val refreshMutex = Mutex()
  private val json = Json { isLenient = true }

  fun isSsoHost(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    return lower.contains("iam.tongji.edu.cn") || lower.contains("ids.tongji.edu.cn")
  }

  fun buildAutofillJs(
    username: String,
    password: String,
    autoSubmit: Boolean,
  ): String {
    val userLit = json.encodeToString(username)
    val passLit = json.encodeToString(password)
    val autoLit = if (autoSubmit) "true" else "false"
    return """
      (function() {
        var userVal = $userLit;
        var passVal = $passLit;
        var autoSubmit = $autoLit;

        if (autoSubmit && window.__campusAutoLoginSubmitted) {
          return 'ALREADY_SUBMITTED';
        }

        function isVisible(el) {
          if (!el) return false;
          var style = window.getComputedStyle(el);
          if (!style || style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
            return false;
          }
          var rect = el.getBoundingClientRect();
          return rect.width > 0 && rect.height > 0;
        }

        function setInputValue(el, value) {
          if (!el) return;
          el.setAttribute('autocapitalize', 'off');
          el.setAttribute('autocorrect', 'off');
          el.setAttribute('spellcheck', 'false');
          var descriptor = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
          if (descriptor && descriptor.set) {
            descriptor.set.call(el, value);
          } else {
            el.value = value;
          }
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        }

        var inputs = Array.prototype.slice.call(document.querySelectorAll('input')).filter(isVisible);
        var exactPass = document.getElementById('j_password');
        var passInput = (exactPass && isVisible(exactPass)) ? exactPass : inputs.find(function(el) {
          return (el.type || '').toLowerCase() === 'password';
        });
        if (!passInput) {
          return 'FORM_NOT_FOUND';
        }

        var captchaRegex = /captcha|kaptcha|verify|sms|otp|code|动态码|验证码/i;
        var captchaInput = inputs.find(function(el) {
          if (el === passInput) return false;
          var hint = (el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '') + ' ' + (el.className || '');
          return captchaRegex.test(hint);
        });
        var hasCaptchaWidget = Array.prototype.slice.call(
          document.querySelectorAll('img[src*="captcha"], img[src*="kaptcha"], .nc_wrapper, .geetest_holder, .yidun')
        ).some(isVisible);

        var exactUser = document.getElementById('j_username');
        var userInput = (exactUser && isVisible(exactUser)) ? exactUser : (inputs.find(function(el) {
          if (el === passInput || el === captchaInput) return false;
          var type = (el.type || 'text').toLowerCase();
          if (type !== 'text' && type !== 'email' && type !== 'tel' && type !== '') return false;
          var hint = (el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '');
          return /user|account|uid|name|学号|账号|工号|用户名/i.test(hint);
        }) || inputs.find(function(el) {
          if (el === passInput || el === captchaInput) return false;
          var type = (el.type || 'text').toLowerCase();
          return type === 'text' || type === 'email' || type === 'tel' || type === '';
        }));

        if (!userInput) {
          return 'FORM_NOT_FOUND';
        }

        setInputValue(userInput, userVal);
        setInputValue(passInput, passVal);

        if (!autoSubmit) {
          return 'FILLED';
        }

        if ((captchaInput && !(captchaInput.value || '').trim()) || hasCaptchaWidget) {
          return 'CAPTCHA_REQUIRED';
        }

        var exactBtn = document.getElementById('loginButton');
        var buttons = Array.prototype.slice.call(
          document.querySelectorAll('button, input[type="submit"], a[role="button"], div[role="button"]')
        ).filter(isVisible);

        var submitBtn = (exactBtn && isVisible(exactBtn)) ? exactBtn : buttons.find(function(btn) {
          var txt = (btn.innerText || btn.value || '').trim();
          var hint = (btn.id || '') + ' ' + (btn.className || '') + ' ' + txt;
          return (btn.type || '').toLowerCase() === 'submit' || /login|submit|signin|登\s*录|sign\s*in/i.test(hint);
        });

        window.__campusAutoLoginSubmitted = true;
        setTimeout(function() {
          if (submitBtn) {
            submitBtn.click();
          } else if (passInput.form) {
            passInput.form.submit();
          }
        }, 120);

        return 'SUBMITTED';
      })();
    """.trimIndent()
  }

  @SuppressLint("SetJavaScriptEnabled")
  suspend fun refreshSession(
    context: Context,
    entryUrl: String,
    expectedHost: String,
    credentials: TongjiCredentials,
    timeoutMs: Long = 20_000L,
    verifyAuthenticated: suspend () -> Boolean,
  ): SsoRefreshResult =
    refreshMutex.withLock {
      val appContext = context.applicationContext
      val deferred = CompletableDeferred<SsoRefreshResult>()
      var webView: WebView? = null
      val runnerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

      try {
        withContext(Dispatchers.Main.immediate) {
          val cm = CookieManager.getInstance()
          cm.setAcceptCookie(true)

          val wv =
            WebView(appContext).apply {
              cm.setAcceptThirdPartyCookies(this, true)
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
          webView = wv

          var submittedOnce = false
          var verifyJob: Job? = null
          var autofillJob: Job? = null

          wv.webViewClient =
            object : WebViewClient() {
              override fun onPageFinished(view: WebView?, url: String?) {
                if (deferred.isCompleted || view == null || url.isNullOrBlank()) return
                CookieManager.getInstance().flush()
                val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")

                if (host.equals(expectedHost, ignoreCase = true)) {
                  verifyJob?.cancel()
                  verifyJob =
                    runnerScope.launch {
                      val maxChecks = if (submittedOnce || url.contains("code=") || url.contains("ticket=")) 5 else 2
                      repeat(maxChecks) { attempt ->
                        delay(if (attempt == 0) 350L else 600L)
                        if (deferred.isCompleted) return@launch
                        val currentUrl = view.url.orEmpty()
                        if (isSsoHost(currentUrl)) return@launch
                        val authed = withContext(Dispatchers.IO) { verifyAuthenticated() }
                        if (authed) {
                          CookieManager.getInstance().flush()
                          deferred.complete(SsoRefreshResult.Success)
                          return@launch
                        }
                      }
                      if (submittedOnce && !deferred.isCompleted) {
                        val currentUrl = view.url.orEmpty()
                        if (!isSsoHost(currentUrl)) {
                          deferred.complete(
                            SsoRefreshResult.Failed(
                              reason = "统一认证已返回但目标站点会话未生效，请点开网页确认",
                              requiresInteractiveLogin = true,
                            )
                          )
                        }
                      }
                    }
                  return
                }

                if (isSsoHost(url)) {
                  verifyJob?.cancel()
                  autofillJob?.cancel()
                  autofillJob =
                    runnerScope.launch {
                      if (submittedOnce) {
                        delay(3000L)
                        if (!deferred.isCompleted && isSsoHost(view.url)) {
                          deferred.complete(
                            SsoRefreshResult.Failed(
                              reason = "统一身份认证未跳转，请检查保存的学号/密码或打开网页登录确认",
                              requiresInteractiveLogin = true,
                            )
                          )
                        }
                        return@launch
                      }

                      val js =
                        buildAutofillJs(
                          username = credentials.username,
                          password = credentials.password,
                          autoSubmit = true,
                        )
                      repeat(8) {
                        delay(350L)
                        if (deferred.isCompleted) return@launch
                        when (evaluateString(view, js)) {
                          "CAPTCHA_REQUIRED" -> {
                            deferred.complete(
                              SsoRefreshResult.Failed(
                                reason = "本次统一身份认证需要验证码/二次验证，请打开网页手动完成",
                                requiresInteractiveLogin = true,
                              )
                            )
                            return@launch
                          }
                          "SUBMITTED", "ALREADY_SUBMITTED" -> {
                            submittedOnce = true
                            return@launch
                          }
                        }
                      }
                    }
                }
              }
            }

          wv.loadUrl(entryUrl)
        }

        withTimeoutOrNull(timeoutMs) { deferred.await() }
          ?: SsoRefreshResult.Failed(
            reason = "后台自动重登超时，请切换网页端完成一次登录",
            requiresInteractiveLogin = true,
          )
      } catch (e: Exception) {
        SsoRefreshResult.Failed(
          reason = "后台自动重登失败：${e.message ?: "未知异常"}",
          requiresInteractiveLogin = true,
        )
      } finally {
        withContext(NonCancellable + Dispatchers.Main.immediate) {
          runnerScope.cancel()
          webView?.stopLoading()
          webView?.destroy()
          webView = null
        }
      }
    }

  private suspend fun evaluateString(webView: WebView, js: String): String =
    suspendCancellableCoroutine { cont ->
      webView.evaluateJavascript(js) { raw ->
        val decoded =
          raw?.let {
            runCatching { json.decodeFromString<String>(it) }.getOrNull()
          }.orEmpty()
        if (cont.isActive) cont.resume(decoded)
      }
    }
}
