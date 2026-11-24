package tv.myliststv.launcher.data

/**
 * How a build number (the CI run number, which is also the Android version code and only ever goes up) is written as a version:
 * 0.1.1 ... 0.1.99, then 0.2.0, 0.2.1 ... 0.2.99, then 0.3.0, and so on. The same rule is in app/build.gradle.kts and
 * .github/workflows/build.yml (release tag and name); change all three together.
 */
object Version {
    fun name(build: Int) = "0.${1 + build / 100}.${build % 100}"

    /** The build number a tag such as "v0.2.5" stands for (205), or null when it is not one of ours. "v0.1.44" gives 44, as it always did. */
    fun build(tag: String): Int? {
        val p = tag.removePrefix("v").split('.')
        if (p.size != 3 || p[0] != "0") return null
        val minor = p[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val patch = p[2].toIntOrNull()?.takeIf { it in 0..99 } ?: return null
        return (minor - 1) * 100 + patch
    }
}
