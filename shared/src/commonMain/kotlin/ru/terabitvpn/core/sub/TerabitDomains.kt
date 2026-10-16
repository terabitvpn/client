package ru.terabitvpn.core.sub

/**
 * Terabit's own address. Subscriptions served from it are recognised as ours (notices, update
 * checks and the like are only ever asked of this host); links of any other provider are left alone.
 */
object TerabitDomains {
    const val MAIN = "terabitvpn.ru"

    /** The names of this service: the site itself and its sub-domains (cabinet, subscription host). */
    private fun ours(host: String) = host == MAIN || host.endsWith(".$MAIN")

    /** How long the link's own domain must stay dead before the app tries another address. */
    const val SWITCH_AFTER_MS = 3 * 60_000L

    fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@').substringBefore(':').lowercase()

    fun isOurs(url: String): Boolean = ours(hostOf(url).removePrefix("www."))

    /** The same link on Terabit's other addresses; there are none for now, so always empty. */
    @Suppress("UNUSED_PARAMETER")
    fun mirrorsOf(url: String): List<String> = emptyList()

    /** A subscription link as it is saved: unchanged (there is no previous domain to move from). */
    @Suppress("UNUSED_PARAMETER")
    fun current(url: String): String = url

    /** The link to save for a subscription that is being added. */
    @Suppress("UNUSED_PARAMETER")
    fun linkToSave(url: String, fetchedFrom: String?, ownAddressFailed: Boolean): String = current(url)
}
