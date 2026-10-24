package eu.tintera.plume.core.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Model zamrznutí, kvůli kterému existuje [readString]: mezipaměť Compose Resources 1.12
 * (`AsyncCache`) hlídá `Mutex`, `stringResource` ho v kompozici bere přes `runBlocking`
 * na hlavním vlákně a suspend `getString` z korutiny na hlavním dispatcheru se za něj řadí.
 *
 * Knihovna sama se v testu na JVM číst nedá (prostředí bere z `Resources.getSystem()`, tedy
 * ze stubu Androidu), proto je tu její zamykání přímo. Až projekt dojde na Compose Resources
 * 1.13 (zámek bez předávání, CMP-6615), test i [readString] můžou zmizet.
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class ResourceReadDeadlockTest {

    @Test
    fun readerOnMainFreezesTheBlockingRead() = runBlocking {
        assertFalse(blockingReadFinishes { mutex -> mutex.withLock { } })
    }

    @Test
    fun readerMovedOffMainLetsTheBlockingReadFinish() = runBlocking {
        assertTrue(blockingReadFinishes { mutex -> withContext(Dispatchers.Default) { mutex.withLock { } } })
    }

    /**
     * 1. Zámek drží korutina na pozadí (jiné čtení prostředku).
     * 2. Korutina na „hlavním" vlákně se za ni zařadí přes [readOnMain].
     * 3. Hlavní vlákno samo čte blokujícím `runBlocking`, jako `stringResource`.
     * 4. Pozadí zámek pustí — Mutex ho předá frontě, ne tomu, kdo zrovna běží.
     *
     * @return jestli blokující čtení doběhlo; když ne, vlákno se uvolní zrušením jeho čekání.
     */
    private suspend fun blockingReadFinishes(readOnMain: suspend (Mutex) -> Unit): Boolean {
        val main = newSingleThreadContext("main")
        val mutex = Mutex()
        val release = CompletableDeferred<Unit>()
        val locked = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Job())
        try {
            scope.launch(Dispatchers.Default) { mutex.withLock { locked.complete(Unit); release.await() } }
            locked.await()

            scope.launch(main) { readOnMain(mutex) }
            delay(100) // ať se korutina z hlavního vlákna stihne zařadit do fronty zámku

            val blockingRead = CompletableDeferred<Job>()
            val finished = CompletableDeferred<Unit>()
            scope.launch(main) {
                runCatching {
                    runBlocking {
                        blockingRead.complete(coroutineContext.job)
                        delay(100) // pozadí pustí zámek, zatímco hlavní vlákno blokuje
                        mutex.withLock { }
                    }
                    finished.complete(Unit)
                }
            }
            val blockingJob = blockingRead.await()
            release.complete(Unit)

            val result = withTimeoutOrNull(2_000) { finished.await() } != null
            blockingJob.cancelAndJoin()
            return result
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            main.close()
        }
    }
}
