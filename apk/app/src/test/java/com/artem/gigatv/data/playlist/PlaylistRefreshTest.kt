package com.artem.gigatv.data.playlist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PlaylistRefreshTest {
    @Test fun retainsSuccessfulSourceOnFailureAndRemovesDeletedSource() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val urls = MutableStateFlow(listOf(server.url("/playlist").toString()))
            val repository = PlaylistRepository(urls)
            server.enqueue(MockResponse().setBody("""{"name":"Movies","items":[{"title":"Film","url":"https://example.test/video"}]}"""))
            repository.refresh()
            assertEquals("Film", repository.playlists.value.single().items.single().title)
            server.enqueue(MockResponse().setResponseCode(503))
            repository.refresh()
            assertEquals("Film", repository.playlists.value.single().items.single().title)
            assertEquals(1, repository.errors.value.size)
            urls.value = emptyList()
            repository.refresh()
            assertTrue(repository.playlists.value.isEmpty())
            assertTrue(repository.errors.value.isEmpty())
        }
    }
}
