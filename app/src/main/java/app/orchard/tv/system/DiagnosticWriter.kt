package app.orchard.tv.system

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Slow storage must not retain an unbounded queue of log strings or a permanently idle thread. */
internal class DiagnosticWriter(capacity: Int = 128) : ThreadPoolExecutor(
    0, 1, 10, TimeUnit.SECONDS, ArrayBlockingQueue(capacity),
    { task -> Thread(task, "AerialDiag").apply { isDaemon = true } },
    DiscardOldestPolicy(),
)
