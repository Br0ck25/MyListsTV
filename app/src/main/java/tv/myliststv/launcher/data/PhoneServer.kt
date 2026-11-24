package tv.myliststv.launcher.data

import tv.myliststv.launcher.App
import tv.myliststv.launcher.R
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * A tiny web page on the TV so a phone on the same Wi-Fi can paste an addon link instead of typing it with the remote.
 * Runs only while the Addons screen is open. [onAdd] gets the pasted link and returns a message to show on the phone.
 */
class PhoneServer(private val port: Int = 8765, private val onAdd: (String, (String) -> Unit) -> Unit) {
    @Volatile private var server: ServerSocket? = null

    /** The address to open on the phone, or null when the TV has no local network address. */
    fun address(): String? = localIp()?.let { "http://$it:$port" }

    fun start() {
        if (server != null) return
        Thread {
            try {
                val s = ServerSocket(port); server = s
                while (!s.isClosed) { val c = s.accept(); Thread { serve(c) }.start() }
            } catch (_: Exception) {}
        }.start()
    }

    fun stop() { try { server?.close() } catch (_: Exception) {}; server = null }

    private fun serve(c: java.net.Socket) { c.use {
        try {
            c.soTimeout = 5000
            val r = c.getInputStream().bufferedReader()
            val request = r.readLine() ?: return@use
            var length = 0
            while (true) { val h = r.readLine() ?: break; if (h.isEmpty()) break; if (h.startsWith("Content-Length:", true)) length = h.substring(15).trim().toInt() }
            val msg: String
            if (request.startsWith("POST")) {
                val buf = CharArray(length.coerceAtMost(4096)); r.read(buf)
                val link = URLDecoder.decode(String(buf).substringAfter("link=", ""), "UTF-8")
                val latch = java.util.concurrent.CountDownLatch(1); var result = "Could not install it."
                onAdd(link) { result = it; latch.countDown() }
                latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
                msg = result
            } else msg = ""
            if (request.contains(" /logo.png")) {
                val png = App.instance.resources.openRawResource(R.raw.logo).use { it.readBytes() }
                c.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nCache-Control: max-age=86400\r\nContent-Length: ${png.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(png); flush() }
                return@use
            }
            val body = (if (request.contains(" /setup")) setupPage() else page(msg)).toByteArray()
            c.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush()
            }
        } catch (_: Exception) {}
    } }

    private fun page(msg: String) = """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>My Lists TV</title>
<body style="font:18px system-ui;background:#0a0b11;color:#eef0f7;padding:24px;max-width:560px;margin:auto">
<img src="/logo.png" width=88 height=88 style="border-radius:20px;display:block;margin:0 0 14px" alt="My Lists">
<h2>Add an addon to My Lists TV</h2><p style="color:#9aa1b8">Paste the addon's install link (it ends in manifest.json, or starts with stremio://).</p>
<form method=post><input name=link style="width:100%;padding:14px;font-size:18px;border-radius:8px;border:0" placeholder="https://.../manifest.json" autofocus>
<p><button style="padding:14px 24px;font-size:18px;border-radius:30px;border:0;background:#ffc65c;font-weight:700">Install on TV</button></p></form>
${if (msg.isNotEmpty()) "<p style='color:#59e0ae;font-weight:700'>$msg</p>" else ""}"""

    private fun setupPage(): String {
        // Every command wraps inside the screen (no sideways scrolling) and says what it does; long ones break anywhere.
        val steps = HomeSetup.steps(localIp() ?: "TV-ADDRESS").mapIndexed { i, (cmd, why) ->
            "<div style='background:#1c1c1e;border-radius:14px;padding:14px;margin:0 0 12px'><div style='color:#aeaeb2;font-size:14px'>Step ${i + 1}</div>" +
                "<p style='margin:4px 0 10px'>$why</p><code style='display:block;background:#000;border-radius:10px;padding:10px;font-size:14px;white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-all'>$cmd</code></div>"
        }.joinToString("")
        return """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>My Lists TV setup</title>
<body style="font:18px system-ui;background:#000;color:#fff;padding:24px;max-width:640px;margin:auto;overflow-wrap:anywhere">
<img src="/logo.png" width=88 height=88 style="border-radius:20px;display:block;margin:0 0 14px" alt="My Lists">
<h2>Make My Lists TV your Home screen</h2>
<p>On the TV, open Settings, then Launcher setup, and try the buttons there first. If your TV will not allow it, turn on <b>Developer options, then USB debugging</b> (and Network debugging if it has it). Then run these commands, in order, on a computer on the same Wi-Fi. Each one says what it does.</p>
$steps
<p style="color:#aeaeb2">Nothing here leaves your network.</p>"""
    }

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress
    } catch (_: Exception) { null }
}
