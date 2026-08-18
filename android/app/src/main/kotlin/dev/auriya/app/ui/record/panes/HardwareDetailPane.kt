package dev.auriya.app.ui.record.panes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.stats.Battery
import dev.auriya.app.data.stats.Cpu
import dev.auriya.app.data.stats.Gpu
import dev.auriya.app.data.stats.Thermal
import dev.auriya.app.ui.record.components.BatteryTelemetryCard
import dev.auriya.app.ui.record.components.CpuTelemetryCard
import dev.auriya.app.ui.record.components.GpuTelemetryCard
import dev.auriya.app.ui.record.components.ThermalTelemetryCard

@Composable
fun HardwareDetailPane(
    cpu: Cpu?,
    gpu: Gpu?,
    thermal: Thermal,
    battery: Battery,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CpuTelemetryCard(cpu = cpu)
            }

            item {
                GpuTelemetryCard(gpu = gpu)
            }

            item {
                ThermalTelemetryCard(thermal = thermal)
            }

            item {
                BatteryTelemetryCard(battery = battery)
            }
        }
    }
}
