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
 * With [onPicture] it also serves /picture, a page that sends a picture from the phone; [onPicture] gets the bytes and returns the message to show.
 */
class PhoneServer(private val port: Int = 8765, private val onPicture: ((ByteArray) -> String)? = null, private val onAdd: (String, (String) -> Unit) -> Unit) {
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

    private fun readHead(i: java.io.InputStream): String {
        val h = java.io.ByteArrayOutputStream(); var tail = 0
        while (h.size() < 16384) { val b = i.read(); if (b < 0) break; h.write(b); tail = (tail shl 8) or b; if (tail == BLANK_LINE) break }
        return h.toString("UTF-8")
    }

    private fun reply(c: java.net.Socket, type: String, body: ByteArray, cache: String = "") = c.getOutputStream().apply {
        write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\n${cache}Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush()
    }

    private fun serve(c: java.net.Socket) { c.use {
        try {
            c.soTimeout = 5000
            val input = java.io.DataInputStream(c.getInputStream().buffered())
            val head = readHead(input)
            val request = head.substringBefore("\r\n")
            val length = Regex("(?i)\\r\\ncontent-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (request.startsWith("POST /upload")) {
                val msg = if (onPicture == null || length !in 1..MAX_PICTURE) "That picture is too big." else onPicture.invoke(ByteArray(length).also { input.readFully(it) })
                reply(c, "text/plain; charset=utf-8", msg.toByteArray()); return@use
            }
            val msg: String
            if (request.startsWith("POST")) {
                val buf = ByteArray(length.coerceAtMost(4096)).also { input.readFully(it) }
                val link = URLDecoder.decode(String(buf).substringAfter("link=", ""), "UTF-8")
                val latch = java.util.concurrent.CountDownLatch(1); var result = "Could not install it."
                onAdd(link) { result = it; latch.countDown() }
                latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
                msg = result
            } else msg = ""
            if (request.contains(" /logo.png")) { reply(c, "image/png", App.instance.resources.openRawResource(R.raw.logo).use { it.readBytes() }, "Cache-Control: max-age=86400\r\n"); return@use }
            val body = (if (request.contains(" /setup")) setupPage() else if (request.contains(" /picture")) picturePage() else page(msg)).toByteArray()
            reply(c, "text/html; charset=utf-8", body)
        } catch (_: Exception) {}
    } }

    /** Shrinks the picture on the phone before sending it (a photo is many megabytes, the TV needs far less). */
    private fun picturePage() = """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>My Lists TV</title>
<body style="font:18px system-ui;background:#0a0b11;color:#eef0f7;padding:24px;max-width:560px;margin:auto">
<img src="/logo.png" width=88 height=88 style="border-radius:20px;display:block;margin:0 0 14px" alt="My Lists">
<h2>Send a picture to My Lists TV</h2><p style="color:#9aa1b8">Choose a picture on this phone. It goes straight to the TV.</p>
<p><input id=f type=file accept="image/*" style="font-size:18px"></p><p id=m style="color:#59e0ae;font-weight:700"></p>
<script>
f.onchange=async()=>{const file=f.files[0];if(!file)return;m.textContent='Sending...';
try{const b=await createImageBitmap(file),k=Math.min(1,1280/Math.max(b.width,b.height)),c=document.createElement('canvas');c.width=Math.round(b.width*k);c.height=Math.round(b.height*k);c.getContext('2d').drawImage(b,0,0,c.width,c.height);
const blob=await new Promise(r=>c.toBlob(r,'image/png'));m.textContent=await(await fetch('/upload',{method:'POST',body:blob})).text()}catch(e){m.textContent='Could not send that picture.'}}
</script>"""

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

    companion object { const val MAX_PICTURE = 12_000_000; private const val BLANK_LINE = 218762506 }    // the bytes CR LF CR LF that end the request head
}
