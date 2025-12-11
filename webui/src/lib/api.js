import { exec } from 'kernelsu-alt';
import { WXClass, WXEventHandler } from 'webuix';
import { addToast } from './Toast.svelte';

const MODULE_PATH = '/data/adb/modules/auriya';

if (typeof window !== 'undefined') {
    window.wx = new WXEventHandler();
}

class AuriyaWX extends WXClass {
    constructor() {
        super();
        this.fileToken = '$auriyaFile';
        this.moduleToken = '$auriya';
        if (typeof window !== 'undefined' && !window[this.fileToken] && window['$auriyafile']) {
            this.fileToken = '$auriyafile';
        }
        this.events = typeof window !== 'undefined' ? window.wx : null;
    }

    get files() {
        return (typeof window !== 'undefined' && this.fileToken) ? window[this.fileToken] : null;
    }

    async runCommand(cmd, cwd = null) {
        if (typeof window !== 'undefined' && !window.ksu && !window.$auriya) {
            console.log(`[Mock Exec] ${cmd}`)
            if (cmd.includes('pidof')) return "1234"
            if (cmd.includes('module.prop')) return "1.0.0"
            if (cmd.includes('current_profile')) return "1"
            if (cmd.includes('ro.board.platform')) return "taro"
            if (cmd.includes('uname')) return "5.10.101"
            if (cmd.includes('scaling_available_governors')) return "schedutil performance powersave"
            if (cmd.includes('cat') && cmd.includes('settings.toml')) return "fas = { enabled = true, default_mode = 'performance' }\ndnd = { default_enable = false }\ncpu = { default_governor = 'schedutil' }"
            return "Mock Output"
        }

        try {
            const { errno, stdout, stderr } = await exec(cmd, cwd ? { cwd } : {});
            return errno === 0 ? stdout.trim() : { error: stderr };
        } catch (e) {
            console.error("Command execution failed:", e);
            return { error: e.message };
        }
    }

    async fileExists(path) {
        if (this.files && typeof this.files.exists === 'function') return this.files.exists(path);
        const result = await this.runCommand(`[ -e "${path}" ] && echo 1 || echo 0`);
        return result === '1';
    }

    async readFile(path) {
        if (this.files && typeof this.files.read === 'function') return this.files.read(path);
        return await this.runCommand(`cat "${path}"`);
    }

    async writeFile(path, content) {
        if (this.files && typeof this.files.write === 'function') return this.files.write(path, content);
        return await this.runCommand(`echo "${content}" > "${path}"`);
    }
}

export const wx = new AuriyaWX();
export const runCommand = wx.runCommand.bind(wx);
export const fileExists = wx.fileExists.bind(wx);
export const readFile = wx.readFile.bind(wx);
export const writeFile = wx.writeFile.bind(wx);
export const showToast = addToast;

export async function openExternalLink(url) {
    if (!url) return;
    const cmd = `am start -a android.intent.action.VIEW -d "${url}"`;
    await runCommand(cmd);
}
