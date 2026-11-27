package tv.myliststv.launcher.data

/** Which pages the top bar shows and in what order: the owner can hide any page except Home and move the rest. */
object Pages {
    /** Every page, in the order a fresh install shows them. */
    val all = listOf("Home", "Movies", "Series", "Search", "Library", "Apps", "Files")

    /** Pages named in [order] come first, in that order; any page it does not name (a new one) follows in the standard order. Hidden ones are dropped, but never Home. */
    fun visible(all: List<String>, order: List<String>, hidden: List<String>): List<String> {
        val named = order.filter { it in all }.distinct()
        return (named + all.filter { it !in named }).filter { it == "Home" || it !in hidden }
    }

    /** What changed the bar, so it is rebuilt only then. */
    fun signature(pages: List<String>) = pages.joinToString("|")
}
