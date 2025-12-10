import { exec } from 'kernelsu'

export async function runCommand(cmd, cwd = null) {
	try {
		if (typeof window !== 'undefined' && !window.ksu && !window.$auriya) {
			console.log(`[Mock Exec] ${cmd}`)
			if (cmd.includes('pidof')) return "1234"
			if (cmd.includes('module.prop')) return "1.0.0"
			if (cmd.includes('current_profile')) return "1"
			if (cmd.includes('ro.board.platform')) return "taro"
			if (cmd.includes('uname')) return "5.10.101"
			if (cmd.includes('scaling_available_governors')) return "schedutil performance powersave"
			if (cmd.includes('cat') && cmd.includes('settings.toml')) return "fas = { enabled = true, default_mode = 'performance' }\ndnd = { default_enable = false }\ncpu = { default_governor = 'schedutil' }"
			if (cmd.includes('STATUS')) return "ENABLED=true PACKAGES=5 OVERRIDE=None LOG_LEVEL=Info"
			return "Mock Output"
		}

		const timeoutPromise = new Promise((_, reject) =>
			setTimeout(() => reject(new Error("Command timed out")), 5000)
		)

		const execPromise = exec(cmd, cwd ? { cwd } : {})
		const { errno, stdout, stderr } = await Promise.race([execPromise, timeoutPromise])

		if (errno !== 0) {
			console.warn(`Command failed: ${cmd}`, stderr)
			return { error: stderr || "Unknown error" }
		}
		return stdout.trim()
	} catch (e) {
		console.error("Exec error:", e)
		return { error: e.message || "Exec exception" }
	}
}
export async function openExternalLink(url) {
	if (!url) return;

	// Command native Android untuk membuka Intent VIEW
	const cmd = `am start -a android.intent.action.VIEW -d "${url}"`;

	console.log(`Opening external link via shell: ${cmd}`);
	await runCommand(cmd);
}

