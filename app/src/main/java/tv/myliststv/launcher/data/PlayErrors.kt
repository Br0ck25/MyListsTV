package tv.myliststv.launcher.data

/** Plain-language reasons for playback failures, each ending with what to do. Pure so tests can check it. */
object PlayErrors {
    enum class Kind { NETWORK, DECODER, FORMAT, OTHER }

    fun describe(httpStatus: Int?, kind: Kind): String = when {
        httpStatus == 403 -> "The stream source is blocked or restricted. Try a different source."
        httpStatus == 404 || httpStatus == 410 -> "The stream link has expired or been removed. Try a different source."
        httpStatus == 429 -> "Too many requests to the stream source. Wait a moment and try again."
        httpStatus != null && httpStatus >= 500 -> "The stream server is not available right now. Try a different source."
        kind == Kind.DECODER -> "This box could not decode the video. Try a lower resolution source."
        kind == Kind.FORMAT -> "The source returned something that is not playable video. Try a different source."
        kind == Kind.NETWORK -> "The connection to the source failed. Check your network, or try a different source."
        else -> "Playback failed. Try a different source."
    }

    /** True when a different source is likely to help, so fallback should run. Network trouble on our side may not be fixed by another source. */
    fun worthTryingAnotherSource(httpStatus: Int?, kind: Kind): Boolean = httpStatus != 429 || kind != Kind.NETWORK
}
