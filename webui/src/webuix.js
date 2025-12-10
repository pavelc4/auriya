import { exec, toast } from 'kernelsu-alt';
import { WXClass, WXEventHandler } from 'webuix';

const MODULE_PATH = '/data/adb/modules/auriya';


// Initialize Global Event Handler (Standard WebUI X pattern)
window.wx = new WXEventHandler();

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

export const runCommand = wx.runCommand.bind(wx);
export const showToast = wx.showToast.bind(wx);
export const fileExists = wx.fileExists.bind(wx);
export const readFile = wx.readFile.bind(wx);
export const writeFile = wx.writeFile.bind(wx);
export const getModuleVersion = wx.getModuleVersion.bind(wx);
export const getAndroidSDK = wx.getAndroidSDK.bind(wx);
export const getKernelVersion = wx.getKernelVersion.bind(wx);




