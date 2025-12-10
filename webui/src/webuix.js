import { exec } from 'kernelsu-alt';
import { WXClass, WXEventHandler } from 'webuix';
import ferrisIcon from './public/ferris_happy.svg';
import ferrisSleepIcon from './public/ferris_sleep.svg';

const MODULE_PATH = '/data/adb/modules/auriya';

// Initialize Global Event Handler (Standard WebUI X pattern)
window.wx = new WXEventHandler();
// ... existing code ...

class AuriyaWX extends WXClass {
    constructor() {
        super();
        // Override automated detection which fails for ID length != 2
        this.fileToken = '$auriyaFile';
        this.moduleToken = '$auriya';

        // Also try to find it via lowercase fallback just in case
        if (!window[this.fileToken] && window['$auriyafile']) {
            this.fileToken = '$auriyafile';
        }

        this.events = window.wx;
    }

    get module() {
        const name = this.findInterface();
        return name ? window[name] : null;
    }

    get files() {
        return this.fileToken ? window[this.fileToken] : null;
    }

    async runCommand(cmd, cwd = null) {
        try {
            const { errno, stdout, stderr } = await exec(cmd, cwd ? { cwd } : {});
            return errno === 0 ? stdout.trim() : { error: stderr };
        } catch (e) {
            console.error("Command execution failed:", e);
            return { error: e.message };
        }
    }

    showToast(message) {
        toast(message);
    }

    async fileExists(path) {
        if (this.files && typeof this.files.exists === 'function') {
            return this.files.exists(path);
        }
        const result = await this.runCommand(`[ -e "${path}" ] && echo 1 || echo 0`);
        return result === '1';
    }

    async readFile(path) {
        if (this.files && typeof this.files.read === 'function') {
            return this.files.read(path);
        }
        return await this.runCommand(`cat "${path}"`);
    }

    async writeFile(path, content) {
        if (this.files && typeof this.files.write === 'function') {
            return this.files.write(path, content);
        }
        return await this.runCommand(`echo "${content}" > "${path}"`);
    }

    async getModuleVersion() {
        if (this.files && typeof this.files.read === 'function') {
            const propPath = `${MODULE_PATH}/module.prop`;
            if (this.files.exists(propPath)) {
                try {
                    const content = this.files.read(propPath);
                    const match = content.match(/^version=(.*)$/m);
                    if (match) return match[1].trim();
                } catch (e) {
                    console.warn("Failed to read version via API", e);
                }
            }
        }
        const output = await this.runCommand(`grep "^version=" ${MODULE_PATH}/module.prop | cut -d= -f2`);
        return typeof output === 'string' ? output : 'Unknown';
    }

    async getAndroidSDK() {
        if (this.module && typeof this.module.getSdk === 'function') {
            return this.module.getSdk();
        }
        return await this.runCommand('getprop ro.build.version.sdk');
    }

    async getKernelVersion() {
        return await this.runCommand('uname -r');
    }
}

export const wx = new AuriyaWX();
function showCustomToast(message) {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'fixed bottom-24 left-1/2 -translate-x-1/2 z-[9999] flex flex-col items-center gap-2 pointer-events-none w-full max-w-sm px-4';
        document.body.appendChild(container);
    }

    const isError = /error|failed|gagal/i.test(message);
    const iconSrc = isError ? ferrisSleepIcon : ferrisIcon;

    const toast = document.createElement('div');
    toast.className = 'flex items-center gap-3 bg-surface-container-high/90 backdrop-blur-md border border-outline/10 shadow-xl text-on-surface p-3 pr-5 rounded-full transform transition-all duration-300 translate-y-10 opacity-0 scale-95';

    toast.innerHTML = `
        <div class="shrink-0 w-8 h-8 flex items-center justify-center">
            <img src="${iconSrc}" class="w-full h-full object-contain" alt="Ferris">
        </div>
        <span class="font-medium text-sm line-clamp-2">${message}</span>
    `;
    container.appendChild(toast);

    requestAnimationFrame(() => {
        toast.classList.remove('translate-y-10', 'opacity-0', 'scale-95');
    });

    setTimeout(() => {
        toast.classList.add('translate-y-4', 'opacity-0');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

export const runCommand = wx.runCommand.bind(wx);
export const showToast = showCustomToast;
export const fileExists = wx.fileExists.bind(wx);
export const readFile = wx.readFile.bind(wx);
export const writeFile = wx.writeFile.bind(wx);
export const getModuleVersion = wx.getModuleVersion.bind(wx);
export const getAndroidSDK = wx.getAndroidSDK.bind(wx);
export const getKernelVersion = wx.getKernelVersion.bind(wx);




