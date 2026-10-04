package dev.saketanand.setwise.llm

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import dev.saketanand.setwise.domain.ai.DownloadFailure
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitGenAiTest {

    @Test
    fun `feature status to availability`() {
        assertEquals(ModelAvailability.Ready, FeatureStatus.AVAILABLE.toModelAvailability())
        assertEquals(ModelAvailability.Downloadable, FeatureStatus.DOWNLOADABLE.toModelAvailability())
        assertEquals(ModelAvailability.Downloading, FeatureStatus.DOWNLOADING.toModelAvailability())
        assertEquals(ModelAvailability.Unavailable, FeatureStatus.UNAVAILABLE.toModelAvailability())
    }

    @Test
    fun `download events, with progress against the announced size`() = runTest {
        val failure = GenAiException("No space", null, GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE)
        val events = flowOf(
            DownloadStatus.DownloadStarted(1_000),
            DownloadStatus.DownloadProgress(400),
            DownloadStatus.DownloadFailed(failure),
            DownloadStatus.DownloadCompleted,
        ).toModelDownloads().toList()

        assertEquals(
            listOf(
                ModelDownload.Progress(0, 1_000),
                ModelDownload.Progress(400, 1_000),
                ModelDownload.Failed("No space", DownloadFailure.NotEnoughSpace),
                ModelDownload.Done,
            ),
            events,
        )
    }

    @Test
    fun `an unknown size stays unknown`() = runTest {
        val events = flowOf(DownloadStatus.DownloadStarted(0), DownloadStatus.DownloadProgress(10)).toModelDownloads().toList()
        assertEquals(listOf(ModelDownload.Progress(0, null), ModelDownload.Progress(10, null)), events)
    }

    @Test
    fun `download errors to what the user can do`() {
        fun failure(code: Int) = GenAiException("", null, code).toDownloadFailure()
        assertEquals(DownloadFailure.NotEnoughSpace, failure(GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE))
        assertEquals(DownloadFailure.NeedsSystemUpdate, failure(GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE))
        assertEquals(DownloadFailure.NeedsSystemUpdate, failure(GenAiException.ErrorCode.AICORE_INCOMPATIBLE))
        assertEquals(DownloadFailure.Other, failure(GenAiException.ErrorCode.BUSY))
    }
}
