package tv.myliststv.launcher.screens

import tv.myliststv.launcher.*

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import tv.myliststv.launcher.ui.Ui

/**
 * Plays a title's trailer inside the app, with YouTube's own embedded player (the only place a trailer's picture comes from), so Back
 * returns to the title page and no other app opens. OK pauses and plays, Left and Right skip 10 seconds. If YouTube will not let that
 * video play embedded, or the player has not started after a while, the trailer opens in the YouTube app instead. The only screen
 * with a WebView; it exists only while a trailer plays.
 */
class TrailerActivity : Activity() {
    private val ORIGIN = "https://mylistsaddon.com"   // the page's own address: YouTube refuses an embed that claims to be youtube.com
    private var web: WebView? = null
    private var started = false
    private var leaving = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var id: String

    private inner class Bridge {
        @JavascriptInterface fun ready() { started = true }
        @JavascriptInterface fun fail(code: Int) { handler.post { elsewhere() } }
        @JavascriptInterface fun ended() { handler.post { finish() } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getStringExtra("id")?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) } ?: run { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val frame = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        frame.addView(Ui.text(this, "Loading the trailer…", 16f, Ui.DIM).apply { gravity = android.view.Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        val w = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            setBackgroundColor(Ui.CLEAR)
            isFocusable = false
            webViewClient = object : WebViewClient() {
                // The page is one embedded player; nothing it links to is opened here.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            }
            addJavascriptInterface(Bridge(), "App")
        }
        web = w
        frame.addView(w, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        w.loadDataWithBaseURL(ORIGIN, page(id), "text/html", "utf-8", null)
        handler.postDelayed({ if (!started) elsewhere() }, 15_000)
    }

    private fun page(id: String) = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%}</style></head>
<body><div id="p"></div><script src="https://www.youtube.com/iframe_api"></script><script>
var player;
function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',
playerVars:{autoplay:1,controls:0,rel:0,playsinline:1,fs:0,modestbranding:1,iv_load_policy:3,disablekb:1,origin:'$ORIGIN'},
events:{onReady:function(e){App.ready();e.target.playVideo();},onError:function(e){App.fail(e.data);},onStateChange:function(e){if(e.data===0)App.ended();}}});}
function toggle(){if(!player)return;player.getPlayerState()===1?player.pauseVideo():player.playVideo();}
function skip(s){if(!player)return;player.seekTo(Math.max(0,player.getCurrentTime()+s),true);}
</script></body></html>"""

    /** YouTube would not play it here: open it in the YouTube app (or a browser) instead. */
    private fun elsewhere() {
        if (leaving) return
        leaving = true
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id"))) }
        catch (_: Exception) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))) } catch (_: Exception) { Toast.makeText(this, "This trailer cannot be played here.", Toast.LENGTH_LONG).show() } }
        finish()
    }

    private fun js(code: String) { web?.evaluateJavascript(code, null) }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { js("toggle()"); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { js("skip(-10)"); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { js("skip(10)"); return true }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onPause() { super.onPause(); web?.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.stopLoading(); it.destroy() }
        web = null
        super.onDestroy()
    }
}
