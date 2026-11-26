package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferTrackerTest {

    @Test
    fun sumsFilesThatAreTransferring() {
        val tracker = TransferTracker()
        tracker.update(fileId = 1, transferring = true, transferredBytes = 50, totalBytes = 100)
        val activity = tracker.update(fileId = 2, transferring = true, transferredBytes = 0, totalBytes = 300)

        assertEquals(2, activity.files)
        assertEquals(50, activity.transferredBytes)
        assertEquals(400, activity.totalBytes)
        assertEquals(0.125f, activity.fraction)
    }

    @Test
    fun finishedFileLeavesTheSummary() {
        val tracker = TransferTracker()
        tracker.update(fileId = 1, transferring = true, transferredBytes = 50, totalBytes = 100)
        val activity = tracker.update(fileId = 1, transferring = false, transferredBytes = 100, totalBytes = 100)

        assertTrue(activity.isIdle)
        assertEquals(0, activity.totalBytes)
    }

    @Test
    fun startedCountsNewFilesOnlyOnce() {
        val tracker = TransferTracker()
        tracker.update(fileId = 1, transferring = true, transferredBytes = 0, totalBytes = 100)
        tracker.update(fileId = 1, transferring = true, transferredBytes = 60, totalBytes = 100)
        tracker.update(fileId = 1, transferring = false, transferredBytes = 100, totalBytes = 100)
        val activity = tracker.update(fileId = 2, transferring = true, transferredBytes = 0, totalBytes = 100)

        // Jeden soubor dojel, druhý začal: počet je pořád 1, ale nový přenos se pozná.
        assertEquals(1, activity.files)
        assertEquals(2, activity.started)
    }

    @Test
    fun unknownSizeDoesNotSkewProgress() {
        val tracker = TransferTracker()
        tracker.update(fileId = 1, transferring = true, transferredBytes = 10, totalBytes = 0)
        val withoutSize = tracker.update(fileId = 1, transferring = true, transferredBytes = 10, totalBytes = 0)
        assertNull(withoutSize.fraction)

        val activity = tracker.update(fileId = 2, transferring = true, transferredBytes = 25, totalBytes = 100)
        assertEquals(2, activity.files)
        assertEquals(0.25f, activity.fraction)
    }

    @Test
    fun clearForgetsFilesButKeepsStartedCount() {
        val tracker = TransferTracker()
        tracker.update(fileId = 1, transferring = true, transferredBytes = 0, totalBytes = 100)
        val cleared = tracker.clear()

        assertTrue(cleared.isIdle)
        assertEquals(1, cleared.started)
    }

    @Test
    fun activitiesOfAccountsAddUp() {
        val first = TransferActivity(files = 1, transferredBytes = 10, totalBytes = 100, started = 3)
        val second = TransferActivity(files = 2, transferredBytes = 30, totalBytes = 100, started = 2)

        assertEquals(TransferActivity(files = 3, transferredBytes = 40, totalBytes = 200, started = 5), first + second)
        assertEquals(first, first + TransferActivity.Idle)
    }

    @Test
    fun onlyListedIgnoresFilesOutsideTheDownloadList() {
        val tracker = TransferTracker(onlyListed = true)
        // Náhled nebo streamované video: stahuje se, ale v seznamu Stahování není.
        val preview = tracker.update(fileId = 1, transferring = true, transferredBytes = 10, totalBytes = 100)
        assertTrue(preview.isIdle)
        assertEquals(0, preview.started)

        tracker.listed(fileId = 2, listed = true)
        val activity = tracker.update(fileId = 2, transferring = true, transferredBytes = 20, totalBytes = 200)
        assertEquals(1, activity.files)
        assertEquals(200, activity.totalBytes)
        assertEquals(1, activity.started)
    }

    @Test
    fun listingMayArriveAfterTheFileUpdate() {
        val tracker = TransferTracker(onlyListed = true)
        tracker.update(fileId = 1, transferring = true, transferredBytes = 10, totalBytes = 100)
        val activity = tracker.listed(fileId = 1, listed = true)

        assertEquals(1, activity.files)
        assertEquals(1, activity.started)
    }

    @Test
    fun completedOrRemovedFileLeavesDownloads() {
        val tracker = TransferTracker(onlyListed = true)
        tracker.listed(fileId = 1, listed = true)
        tracker.update(fileId = 1, transferring = true, transferredBytes = 10, totalBytes = 100)
        val removed = tracker.listed(fileId = 1, listed = false)

        assertTrue(removed.isIdle)
        // Dál se stahuje (třeba jako přehrávané video), ale jako stažení ze seznamu už ne.
        assertTrue(tracker.update(fileId = 1, transferring = true, transferredBytes = 20, totalBytes = 100).isIdle)
    }

    @Test
    fun pausedDownloadStaysListedButIsNotCounted() {
        val tracker = TransferTracker(onlyListed = true)
        tracker.listed(fileId = 1, listed = true)
        tracker.update(fileId = 1, transferring = true, transferredBytes = 10, totalBytes = 100)
        val paused = tracker.update(fileId = 1, transferring = false, transferredBytes = 10, totalBytes = 100)
        assertTrue(paused.isIdle)

        val resumed = tracker.update(fileId = 1, transferring = true, transferredBytes = 10, totalBytes = 100)
        assertEquals(1, resumed.files)
        // Obnovené stahování je nový přenos: na Androidu znovu naplánuje úlohu.
        assertEquals(2, resumed.started)
    }
}
