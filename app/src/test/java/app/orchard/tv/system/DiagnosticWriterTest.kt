package app.orchard.tv.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiagnosticWriterTest {
    @Test
    fun slowDiskKeepsOnlyTheNewestPendingRecordsWithoutBlockingTheCaller() {
        val writer = DiagnosticWriter(2)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val written = Collections.synchronizedList(mutableListOf<Int>())
        try {
            writer.execute { started.countDown(); release.await(5, TimeUnit.SECONDS) }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(100) { value -> writer.execute { written.add(value) } }
            assertEquals(2, writer.queue.size)
            release.countDown()
            writer.shutdown()
            assertTrue(writer.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(listOf(98, 99), written)
        } finally {
            release.countDown()
            writer.shutdownNow()
        }
    }

    @Test
    fun idleWriterReleasesItsThread() {
        val writer = DiagnosticWriter()
        writer.setKeepAliveTime(20, TimeUnit.MILLISECONDS)
        val done = CountDownLatch(1)
        try {
            writer.execute { done.countDown() }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (writer.poolSize > 0 && System.nanoTime() < deadline) Thread.sleep(5)
            assertEquals(0, writer.poolSize)
        } finally {
            writer.shutdownNow()
        }
    }
}
