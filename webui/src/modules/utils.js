import { runCommand as apiRunCommand } from '../webuix.js'

export async function runCommand(cmd, cwd = null, timeout = 5000) {
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

	const output = await apiRunCommand(cmd, cwd);
	if (output && output.error) {
		console.warn(`Command failed: ${cmd}`, output.error)
		return output
	}
	return output
}
export async function openExternalLink(url) {
	if (!url) return;

	const cmd = `am start -a android.intent.action.VIEW -d "${url}"`;

	console.log(`Opening external link via shell: ${cmd}`);
	await runCommand(cmd);
}

