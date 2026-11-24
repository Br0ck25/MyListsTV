package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.myliststv.launcher.App

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class AccountWatchedTest {
    @Test fun aCompleteShowDoesNotTickEpisodesTheAccountDoesNotList() {
        assertTrue(AccountWatched.parse("""{"ok":true,"movies":["tt9"],"shows":[{"id":"tt1","complete":true,"episodes":["1:1","1:2"]}]}"""))
        assertTrue(AccountWatched.showDone("tt1")); assertTrue(AccountWatched.movieDone("tt9"))
        assertTrue(AccountWatched.episodeDone("tt1", "tt1:1:2"))
        assertFalse("an episode the account does not list (not aired yet)", AccountWatched.episodeDone("tt1", "tt1:9:9"))
    }

    @Test fun anEpisodeTheAccountGaveCanBeTakenOffByHand() {
        AccountWatched.parse("""{"ok":true,"movies":[],"shows":[{"id":"tt1","complete":true,"episodes":["1:1","1:2"]}]}""")
        AccountWatched.markEpisode("tt1", "tt1:1:2", false)
        assertFalse(AccountWatched.episodeDone("tt1", "tt1:1:2")); assertTrue(AccountWatched.episodeDone("tt1", "tt1:1:1"))
        assertFalse("no longer complete once an episode is taken off", AccountWatched.showDone("tt1"))
        AccountWatched.markEpisode("tt1", "tt1:2:1", true)
        assertTrue(AccountWatched.episodeDone("tt1", "tt1:2:1"))
    }

    @Test fun aCheckTakenOffByHandDoesNotComeBackWhileTheAccountStillListsIt() {
        val answer = """{"ok":true,"movies":[],"shows":[{"id":"tt7","complete":false,"episodes":["1:1","1:2"]}]}"""
        AccountWatched.parse(answer)
        AccountWatched.markEpisode("tt7", "tt7:1:2", false)
        AccountWatched.parse(answer, prune = true)   // the next fetch: the account has not removed it yet
        assertFalse(AccountWatched.episodeDone("tt7", "tt7:1:2")); assertTrue(AccountWatched.episodeDone("tt7", "tt7:1:1"))
        AccountWatched.parse("""{"ok":true,"movies":[],"shows":[{"id":"tt7","complete":false,"episodes":["1:1"]}]}""", prune = true)   // now it has
        AccountWatched.markEpisode("tt7", "tt7:1:2", true)
        assertTrue("marking it again works", AccountWatched.episodeDone("tt7", "tt7:1:2"))
    }

    @Test fun badAnswersLeaveAnEmptyRecord() {
        assertFalse(AccountWatched.parse("not json")); assertFalse(AccountWatched.parse(""))
        assertFalse(AccountWatched.movieDone("tt9"))
    }
}
