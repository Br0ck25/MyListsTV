package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test

class JsonTest {

    @Test fun parsesTheLiveMyListsManifestShape() {
        val text = """{"id":"app.my-list","version":"1.5.8","name":"My Lists",
          "resources":["catalog",{"name":"meta","types":["movie","series"],"idPrefixes":["tt","tmdb:"]}],
          "types":["movie","series"],"idPrefixes":["tt"],
          "catalogs":[{"type":"movie","id":"chart:combined:trending_movies","name":"Trending","extra":[{"name":"skip","isRequired":false}]},
          {"type":"movie","id":"search_movies","name":"Movies","extra":[{"name":"search","isRequired":true},{"name":"skip","isRequired":false}]}]}"""
        val m = Json.parseManifest(text)
        assertEquals("My Lists", m.name)
        assertTrue(m.provides("catalog")); assertTrue(m.provides("meta")); assertFalse(m.provides("stream"))
        assertEquals(listOf("tt", "tmdb:"), m.resources["meta"]!!.idPrefixes)
        assertEquals(2, m.catalogs.size)
        assertFalse(m.catalogs[0].searchRequired)
        assertTrue(m.catalogs[1].searchRequired); assertTrue(m.catalogs[1].supportsSearch)
    }

    @Test fun parsesCatalogItems() {
        val text = """{"metas":[{"id":"tt1160419","type":"movie","name":"Dune","poster":"https://p/x.jpg","releaseInfo":"2021","genres":["Adventure"],"imdbRating":"8.0"},{"id":"","name":"bad"}]}"""
        val l = Json.parseMetas(text)
        assertEquals(1, l.size)
        assertEquals("Dune", l[0].name); assertEquals("2021", l[0].year); assertEquals("8.0", l[0].rating)
    }

    @Test fun parsesEpisodesInOrder() {
        val text = """{"meta":{"id":"tt1","type":"series","name":"Show","videos":[
          {"id":"tt1:1:2","season":1,"episode":2,"title":"Two"},{"id":"tt1:1:1","season":1,"episode":1,"name":"One"},{"id":"tt1:0:1","season":0,"episode":1,"title":"Special"}]}}"""
        val m = Json.parseMeta(text)!!
        assertEquals(listOf("tt1:0:1", "tt1:1:1", "tt1:1:2"), m.videos.map { it.id })
        assertEquals("One", m.videos[1].title)
    }

    @Test fun parsesAMixedListOfShowsAndMovies() {
        // A My Lists channel such as the Arrowverse timeline: one series whose videos are episodes of several shows and movies, in play order.
        val text = """{"meta":{"id":"channel_channel-arrow","type":"series","name":"Timeline","videos":[
          {"id":"tt2193021:1:1","title":"Pilot","season":1,"episode":1,"released":"2012-10-10T11:00:00.000Z"},
          {"id":"tt4532038","title":"A Movie","season":1,"episode":2},
          {"id":"tt3107288:1:1","title":"Flash Pilot","season":1,"episode":3}]}}"""
        val m = Json.parseMeta(text)!!
        assertEquals(listOf("tt2193021:1:1", "tt4532038", "tt3107288:1:1"), m.videos.map { it.id })
        assertEquals(listOf("series", "movie", "series"), m.videos.map { StreamResolver.typeFor(m.type, m.id, it.id) })
        assertEquals("https://x.test/i/T/tv/meta/series/channel_channel-arrow.json", Urls.meta("https://x.test/i/T/tv", "series", m.id))
    }

    @Test fun keepsOnlyDirectUrlStreams() {
        val text = """{"streams":[
          {"name":"[RD+] Addon 1080p","title":"Movie.1080p 4.2 GB","url":"https://cdn/x.mkv","behaviorHints":{"filename":"x.mkv","bingeGroup":"g"}},
          {"name":"Torrent","infoHash":"abcd"},
          {"name":"Web","externalUrl":"https://site"}]}"""
        val l = Json.parseStreams(text, "Addon", "https://a/manifest.json")
        assertEquals(1, l.size)
        assertEquals(1080, l[0].resolution); assertTrue(l[0].cached); assertEquals("x.mkv", l[0].filename); assertEquals("g", l[0].bingeGroup)
    }

    @Test fun buildsAddonUrls() {
        assertEquals("https://h/x/catalog/movie/chart:combined:trending_movies.json", Urls.catalog("https://h/x", "movie", "chart:combined:trending_movies"))
        assertEquals("https://h/x/catalog/movie/search_movies/search=dune%20part&skip=20.json", Urls.catalog("https://h/x", "movie", "search_movies", 20, "dune part"))
        assertEquals("https://h/x/stream/series/tt1:1:2.json", Urls.stream("https://h/x", "series", "tt1:1:2"))
    }

    @Test fun normalizesAddonLinks() {
        assertEquals("https://a.example/x/manifest.json", AddonStore.normalize("stremio://a.example/x/manifest.json"))
        assertEquals("https://a.example/x/manifest.json", AddonStore.normalize(" https://a.example/x/ "))
        assertNull(AddonStore.normalize("not a url"))
    }
}
