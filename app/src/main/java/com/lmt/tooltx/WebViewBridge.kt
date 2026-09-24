package com.lmt.tooltx

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object WebViewBridge {

    @Volatile
    private var webView: WebView? = null

    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun attach(wv: WebView?) {
        webView = wv
    }

    @JvmStatic
    fun detach() {
        webView = null
    }

    @JvmStatic
    fun pauseWebView() {
        val wv = webView ?: return
        main.post {
            try { wv.pauseTimers() } catch (_: Throwable) {}
            try { wv.onPause() } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    fun resumeWebView() {
        val wv = webView ?: return
        main.post {
            try { wv.onResume() } catch (_: Throwable) {}
            try { wv.resumeTimers() } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    fun navigate(url: String) {
        val wv = webView ?: return
        main.post { wv.loadUrl(url) }
    }

    @JvmStatic
    fun currentUrl(): String? {
        val wv = webView ?: return null
        val result = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        main.post {
            try {
                result.set(wv.url)
            } catch (e: Throwable) {
                result.set(null)
            }
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
        return result.get()
    }

    @Volatile
    private var muted = false

    @JvmStatic
    fun isMuted(): Boolean = muted

    @JvmStatic
    fun toggleMute(): Boolean {
        if (muted) unmutePage() else mutePage()
        return muted
    }

    @JvmStatic
    fun unmuteState() {
        muted = false
    }

    @JvmStatic
    fun unmutePage() {
        muted = false
        val wv = webView ?: return
        val js = "(function(){try{" +
            "function showA(){document.querySelectorAll('audio,video').forEach(function(a){a.muted=false;if(a.paused){try{a.play().catch(function(){});}catch(_){}}});}" +
            "showA();" +
            "window.__wsMuteKill=false;" +
            "window.__wsShimDone=0;" +
            "var wvx=window.__wsMuteState;" +
            "if(wvx&&wvx.origPlay){HTMLMediaElement.prototype.play=wvx.origPlay;}" +
            "if(wvx&&wvx.origAC){window.AudioContext=wvx.origAC;window.webkitAudioContext=wvx.origAC;}" +
            "if(window.__wsMuteObs){try{window.__wsMuteObs.disconnect();}catch(_){}}" +
            "(function(AC){if(!AC)return;try{var c=new AC();if(c.resume){c.resume().catch(function(){});}}catch(_){}})(window.AudioContext||window.webkitAudioContext);" +
            "if(!window.__wsShimDummy){window.__wsShimDummy=1;}" +
            "(function(){if(window.__wsMuteKill===undefined){return;}})();" +
            "}catch(e){}})();"
        main.post {
            try {
                wv.evaluateJavascript(js, null)
            } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    fun mutePage() {
        muted = true
        val wv = webView ?: return
        val js = "(function(){try{" +
            "var _p=HTMLMediaElement.prototype.play,_ac=window.AudioContext||window.webkitAudioContext;" +
            "if(!window.__snd){window.__snd={p:_p,ac:_ac};}" +
            "function killA(){document.querySelectorAll('audio,video').forEach(function(a){a.muted=true;});}" +
            "killA();" +
            "var op=HTMLMediaElement.prototype.play;" +
            "HTMLMediaElement.prototype.play=function(){this.muted=true;return op.apply(this,arguments);};" +
            "(function(AC){if(!AC)return;var ctor=function(){var c=new AC();if(c.suspend){c.suspend().catch(function(){});}return c;};ctor.prototype=AC.prototype;window.AudioContext=ctor;window.webkitAudioContext=ctor;})(window.AudioContext||window.webkitAudioContext);" +
            "new MutationObserver(function(){killA();}).observe(document.body,{subtree:true,childList:true});" +
            "}catch(e){}})();"
        main.post {
            try {
                wv.evaluateJavascript(js, null)
            } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    @Throws(Throwable::class)
    fun evalJs(expr: String, timeoutMs: Long): String? {
        val wv = webView ?: return null
        val result = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        main.post {
            try {
                wv.evaluateJavascript(expr) { value ->
                    result.set(value)
                    latch.countDown()
                }
            } catch (e: Throwable) {
                result.set(null)
                latch.countDown()
            }
        }
        if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            return result.get()
        }
        return null
    }

    @JvmStatic
    fun installWsShim() {
        val wv = webView ?: return
        val js = "if(window.__wsCapShim)return;window.__wsCapShim=1;" +
            "(function(){" +
            "window.__wsLog=[];window.__wsLogMax=2000;" +
            "function push(e){if(!e)return;if(window.__wsLog.length>=window.__wsLogMax)window.__wsLog.shift();window.__wsLog.push(e);}" +
            "var Orig=window.WebSocket;" +
            "function Wrapped(url,protocols){" +
            "var w=new Orig(url,protocols);" +
            "try{w.addEventListener('message',function(ev){var d=ev.data;" +
            "if(typeof d==='string'){push({t:Date.now(),k:'t',d:d});}" +
            "else if(d&&d.byteLength!==undefined){try{var u8=new Uint8Array(d.slice?d.slice(0,1500):d);var hex='';for(var i=0;i<u8.length;i++)hex+=('0'+u8[i].toString(16)).slice(-2);push({t:Date.now(),k:'b',d:hex});}catch(_){}}" +
            "else if(d&&d.data){try{var dd=new Uint8Array(d.data.slice?d.data.slice(0,1500):d.data);var h2='';for(var i=0;i<dd.length;i++)h2+=('0'+dd[i].toString(16)).slice(-2);push({t:Date.now(),k:'b',d:h2});}catch(_){}}" +
            "});}catch(_){}" +
            "return w;}" +
            "Wrapped.prototype=Orig.prototype;" +
            "Wrapped.CONNECTING=Orig.CONNECTING;Wrapped.OPEN=Orig.OPEN;Wrapped.CLOSING=Orig.CLOSING;Wrapped.CLOSED=Orig.CLOSED;" +
            "window.WebSocket=Wrapped;" +
            "})();"
        main.post {
            try {
                wv.evaluateJavascript(js, null)
            } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    fun getWsLog(): String? {
        return evalJs("(function(){try{return JSON.stringify(window.__wsLog||[]);}catch(e){return '[]';}})()", 4000)
    }
}