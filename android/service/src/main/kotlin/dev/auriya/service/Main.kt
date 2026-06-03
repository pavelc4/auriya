package dev.auriya.service

import android.os.Looper
import android.util.Log
import dev.auriya.service.actuator.DisplayActuator
import dev.auriya.service.actuator.DnDActuator

import dev.auriya.service.actuator.SettingsHelper
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

object Main {
    private const val TAG = "AuriyaService"
    private const val WRITE_DEBOUNCE_MS = 50L

    @JvmStatic
    fun main(args: Array<String>) {
        Log.i(TAG, "starting Auriya companion service")

        Looper.prepareMainLooper()

        val lockFile = LockFile(File(ConfigPaths.COMPANION_LOCK_FILE))
        if (!lockFile.tryAcquire()) {
            Log.e(TAG, "another companion is already running; exiting")
            exitProcess(1)
        }
        Runtime.getRuntime().addShutdownHook(Thread { lockFile.close() })

        val writer = StatusWriter(File(ConfigPaths.STATUS_FILE))
        val aggregator = Aggregator(writer)
        val sink = SensorSink { update -> aggregator.push(update) }

        val settings = SettingsHelper()
        val taskStack = TaskStackSensor(sink)
        val power = PowerSensor(sink)
        val zen = ZenSensor(sink)
        val dnd = DnDActuator()
        val display = DisplayActuator(settings)
        val cmdReader = CmdReader(File(ConfigPaths.CMD_FILE)) { cmd ->
            cmd.dnd?.let { dnd.apply(it) }
            cmd.refreshRate?.let { display.apply(it) }
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

    private class Aggregator(private val writer: StatusWriter) {
        private val state = AtomicReference(SensorSnapshot())
        private val pending = AtomicReference<Long?>(null)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private val lock = Object()
        private val writerThread = Thread(::runWriter, "auriya-status-writer").apply {
            isDaemon = true
            start()
        }

        fun push(update: SensorSnapshot) {
            while (true) {
                val current = state.get()
                val merged = current.merge(update)
                if (merged == current) return
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
                if (target == 0L) continue
                val now = System.currentTimeMillis()
                if (now < target) {
                    try { Thread.sleep(target - now) } catch (_: InterruptedException) { return }
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
