package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.testing.FakeOnDeviceModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloaderTest {

    @Test
    fun `waits to start until bytes arrive, then shows percent, then done`() = runTest(UnconfinedTestDispatcher()) {
        val model = FakeOnDeviceModel(
            ModelAvailability.Downloadable,
        ).apply {
            downloadStepMs = 1_000
            downloadSteps = listOf(
                ModelDownload.Progress(0, 1_000),
                ModelDownload.Progress(250, 1_000),
                ModelDownload.Progress(1_000, 1_000),
                ModelDownload.Done,
            )
        }
        val downloader = ModelDownloader(model, backgroundScope)
        val seen = mutableListOf<DownloadState>()
        backgroundScope.launch { downloader.state.toList(seen) }

        downloader.start()
        advanceTimeBy(10_000)

        assertEquals(
            listOf(
                DownloadState.Idle,
                DownloadState.WaitingToStart,
                DownloadState.Downloading(25),
                DownloadState.Downloading(100),
                DownloadState.Done,
            ),
            seen,
        )
    }

    @Test
    fun `an unknown size shows progress without a percent`() = runTest(UnconfinedTestDispatcher()) {
        val model = FakeOnDeviceModel().apply { downloadSteps = listOf(ModelDownload.Progress(5_000, null)) }
        val downloader = ModelDownloader(model, backgroundScope)

        downloader.start()

        assertEquals(DownloadState.Downloading(null), downloader.state.value)
    }

    @Test
    fun `a download that throws shows as failed instead of crashing`() = runTest(UnconfinedTestDispatcher()) {
        val model = object : OnDeviceModel by FakeOnDeviceModel() {
            override fun download(): Flow<ModelDownload> = throw IllegalStateException("AICore not ready")
        }
        val downloader = ModelDownloader(model, backgroundScope)

        downloader.start()

        assertEquals(DownloadState.Failed(DownloadFailure.Other), downloader.state.value)
    }
}
