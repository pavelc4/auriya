package dev.auriya.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import dev.auriya.service.actuator.DnDActuator
import dev.auriya.service.io.CmdReader
import dev.auriya.service.io.StatusWriter
import dev.auriya.service.lock.LockFile
import dev.auriya.service.sensor.PowerSensor
import dev.auriya.service.sensor.SensorSink
import dev.auriya.service.sensor.SensorSnapshot
import dev.auriya.service.sensor.TaskStackSensor
import dev.auriya.service.sensor.ZenSensor
import dev.auriya.shared.config.ConfigPaths
import dev.auriya.shared.model.SystemStatus
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/**
 * Companion entry point.
 *
 * Launched by `service.sh`:
 *
 *     app_process \
 *         -Djava.class.path=/system/etc/auriya/service.apk \
 *         /system/bin --nice-name=AuriyaSysMon \
 *         dev.auriya.service.Main
 *
 * Responsibilities:
 *   1. Acquire a singleton flock so a second copy refuses to start.
 *   2. Bootstrap an ActivityThread so we can use Context APIs from a
 *      headless process. Without that bootstrap every getSystemService
 *      call throws because there is no LoadedApk to look services up
 *      against.
 *   3. Wire the three sensors to a debounced aggregator.
 *   4. Atomically write each merged snapshot to the status file.
 *   5. Park on the main looper for the rest of the process lifetime.
 */
object Main {
    private const val TAG = "AuriyaService"
    private const val WRITE_DEBOUNCE_MS = 50L

    @JvmStatic
    fun main(args: Array<String>) {
        Log.i(TAG, "starting Auriya companion service")

        val lockFile = LockFile(File(ConfigPaths.COMPANION_LOCK_FILE))
        if (!lockFile.tryAcquire()) {
            Log.e(TAG, "another companion is already running; exiting")
            exitProcess(1)
        }
        // Hold the lock for the JVM lifetime; kernel releases it on
        // exit and the daemon watches it for liveness.
        Runtime.getRuntime().addShutdownHook(Thread { lockFile.close() })

        Looper.prepareMainLooper()
        val context = bootstrapContext() ?: run {
            Log.e(TAG, "failed to bootstrap system context; cannot continue")
            exitProcess(2)
        }

        val writer = StatusWriter(File(ConfigPaths.STATUS_FILE))
        val aggregator = Aggregator(writer)

        val sink = SensorSink { update -> aggregator.push(update) }

        val taskStack = TaskStackSensor(context, sink)
        val power = PowerSensor(context, sink)
        val zen = ZenSensor(context, sink)

        val dnd = DnDActuator(context)
        val cmdReader = CmdReader(File(ConfigPaths.CMD_FILE)) { cmd ->
            cmd.dnd?.let { dnd.apply(it) }
            // refresh_rate handling lands when the display actuator
            // is implemented.
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            cmdReader.stop()
            taskStack.stop()
            power.stop()
            zen.stop()
        })

        taskStack.start()
        power.start()
        zen.start()
        cmdReader.start()

        Log.i(TAG, "sensors + actuator started, parking main looper")
        Looper.loop()
    }

    /**
     * Reflectively spin up an `ActivityThread` so we can call
     * `getSystemService` and friends. `ActivityThread` is a hidden
     * class so a direct import would fail the Kotlin compiler — we
     * reach for it via reflection at runtime instead.
     */
    @SuppressLint("PrivateApi")
    private fun bootstrapContext(): Context? = try {
        val cls = Class.forName("android.app.ActivityThread")
        val activityThread = cls.getMethod("systemMain").invoke(null)
        cls.getMethod("getSystemContext").invoke(activityThread) as? Context
    } catch (t: Throwable) {
        Log.e(TAG, "ActivityThread bootstrap failed", t)
        null
    }

    /**
     * Coalesces partial sensor updates and flushes them to disk no
     * more than once per [WRITE_DEBOUNCE_MS] window. We don't want to
     * spam the daemon with one inotify event per micro-change when
     * several sensors fire close together (e.g. screen-on + foreground
     * switch happening in the same 20ms).
     */
    private class Aggregator(private val writer: StatusWriter) {
        private val state = AtomicReference(SensorSnapshot())
        private val pending = AtomicReference<Long?>(null)
        private val lock = Object()
        private val writerThread = Thread(::runWriter, "auriya-status-writer").apply {
            isDaemon = true
            start()
        }

        fun push(update: SensorSnapshot) {
            while (true) {
                val current = state.get()
                val merged = current.merge(update)
                if (merged == current) return // nothing actually changed
                if (state.compareAndSet(current, merged)) break
            }
            synchronized(lock) {
                pending.set(System.currentTimeMillis() + WRITE_DEBOUNCE_MS)
                lock.notifyAll()
            }
        }

        private fun runWriter() {
            while (!Thread.currentThread().isInterrupted) {
                val target: Long = synchronized(lock) {
                    while (pending.get() == null) {
                        try { lock.wait() } catch (_: InterruptedException) { return }
                    }
                    pending.get() ?: 0L
                }
                if (target == 0L) {
                    // pending was cleared between wait() and read;
                    // loop and re-wait.
                    continue
                }
                val now = System.currentTimeMillis()
                if (now < target) {
                    try { Thread.sleep(target - now) } catch (_: InterruptedException) { return }
                    // Another update may have shifted the deadline
                    // forward while we slept; re-check before flushing.
                    val newTarget = pending.get() ?: continue
                    if (System.currentTimeMillis() < newTarget) continue
                }
                val snapshot = state.get().toSystemStatus()
                pending.set(null)
                writer.write(snapshot) { t ->
                    Log.e(TAG, "status write failed", t)
                }
            }
        }
    }
}

private fun SensorSnapshot.toSystemStatus(): SystemStatus = SystemStatus(
    focusedApp = focusedApp,
    focusedPid = focusedPid,
    focusedUid = focusedUid,
    screenAwake = screenAwake,
    batterySaver = batterySaver,
    zenMode = zenMode,
)
