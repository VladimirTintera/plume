package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UploadTrackerTest {

    @Test
    fun sumsFilesThatAreUploading() {
        val tracker = UploadTracker()
        tracker.update(fileId = 1, uploading = true, uploadedBytes = 50, totalBytes = 100)
        val activity = tracker.update(fileId = 2, uploading = true, uploadedBytes = 0, totalBytes = 300)

        assertEquals(2, activity.files)
        assertEquals(50, activity.uploadedBytes)
        assertEquals(400, activity.totalBytes)
        assertEquals(0.125f, activity.fraction)
    }

    @Test
    fun finishedFileLeavesTheSummary() {
        val tracker = UploadTracker()
        tracker.update(fileId = 1, uploading = true, uploadedBytes = 50, totalBytes = 100)
        val activity = tracker.update(fileId = 1, uploading = false, uploadedBytes = 100, totalBytes = 100)

        assertTrue(activity.isIdle)
        assertEquals(0, activity.totalBytes)
    }

    @Test
    fun startedCountsNewFilesOnlyOnce() {
        val tracker = UploadTracker()
        tracker.update(fileId = 1, uploading = true, uploadedBytes = 0, totalBytes = 100)
        tracker.update(fileId = 1, uploading = true, uploadedBytes = 60, totalBytes = 100)
        tracker.update(fileId = 1, uploading = false, uploadedBytes = 100, totalBytes = 100)
        val activity = tracker.update(fileId = 2, uploading = true, uploadedBytes = 0, totalBytes = 100)

        // Jeden soubor dojel, druhý začal: počet je pořád 1, ale nové nahrávání se pozná.
        assertEquals(1, activity.files)
        assertEquals(2, activity.started)
    }

    @Test
    fun unknownSizeDoesNotSkewProgress() {
        val tracker = UploadTracker()
        tracker.update(fileId = 1, uploading = true, uploadedBytes = 10, totalBytes = 0)
        val withoutSize = tracker.update(fileId = 1, uploading = true, uploadedBytes = 10, totalBytes = 0)
        assertNull(withoutSize.fraction)

        val activity = tracker.update(fileId = 2, uploading = true, uploadedBytes = 25, totalBytes = 100)
        assertEquals(2, activity.files)
        assertEquals(0.25f, activity.fraction)
    }

    @Test
    fun clearForgetsFilesButKeepsStartedCount() {
        val tracker = UploadTracker()
        tracker.update(fileId = 1, uploading = true, uploadedBytes = 0, totalBytes = 100)
        val cleared = tracker.clear()

        assertTrue(cleared.isIdle)
        assertEquals(1, cleared.started)
    }

    @Test
    fun activitiesOfAccountsAddUp() {
        val first = UploadActivity(files = 1, uploadedBytes = 10, totalBytes = 100, started = 3)
        val second = UploadActivity(files = 2, uploadedBytes = 30, totalBytes = 100, started = 2)

        assertEquals(UploadActivity(files = 3, uploadedBytes = 40, totalBytes = 200, started = 5), first + second)
        assertEquals(first, first + UploadActivity.Idle)
    }
}
