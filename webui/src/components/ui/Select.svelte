<script>
    import { createEventDispatcher } from "svelte";
    import { clickOutside } from "../../lib/actions";
    import { slide } from "svelte/transition";
    import Icon from "./Icon.svelte";

    export let value = "";
    export let options = []; // Array of { value, label } or simple strings/numbers
    export let placeholder = "Select option";
    export let disabled = false;

    const dispatch = createEventDispatcher();
    let isOpen = false;

    $: selectedOption = options.find((o) => getVal(o) === value);
    $: label = selectedOption ? getLabel(selectedOption) : placeholder;

    function getVal(option) {
        return typeof option === "object" ? option.value : option;
    }

    function getLabel(option) {
        return typeof option === "object" ? option.label : option;
    }

    function toggle() {
        if (!disabled) isOpen = !isOpen;
    }

    function close() {
        isOpen = false;
    }

    function select(option) {
        value = getVal(option);
        dispatch("change", { value });
        close();
    }
</script>

<div class="relative w-full" use:clickOutside on:click_outside={close}>
    <!-- Trigger -->
    <button
        type="button"
        on:click={toggle}
        {disabled}
        class="w-full flex items-center justify-between bg-surface-container-high h-12 rounded-2xl px-4 text-left transition-all duration-200 border border-transparent focus:outline-none focus:border-on-surface-variant/30 {disabled
            ? 'opacity-50 cursor-not-allowed'
            : 'cursor-pointer hover:bg-surface-variant/20'}"
    >
        <span class="block truncate text-sm font-medium text-on-surface">
            {label}
        </span>
        <div
            class="transition-transform duration-300 text-on-surface-variant"
            class:rotate-180={isOpen}
        >
            <Icon name="keyboard_arrow_down" className="text-xl" />
        </div>
    </button>

    <!-- Dropdown -->
    {#if isOpen}
        <div
            transition:slide={{ duration: 200 }}
            class="absolute z-50 w-full mt-2 bg-surface-container-high rounded-2xl shadow-xl overflow-hidden border border-surface-variant/10"
        >
            <div class="max-h-96 overflow-y-auto py-2 options-scroll">
                {#each options as option}
                    {@const isSelected = getVal(option) === value}
                    <button
                        type="button"
                        on:click={() => select(option)}
                        class="w-full flex items-center justify-between px-4 py-3 text-sm text-left transition-colors hover:bg-surface-variant/20 {isSelected
                            ? 'text-on-surface font-bold bg-surface-variant/30'
                            : 'text-on-surface'}"
                    >
                        <span>{getLabel(option)}</span>
                        {#if isSelected}
                            <Icon name="check" className="text-lg" />
                        {/if}
                    </button>
                {/each}
            </div>
        </div>
    {/if}
</div>

<style>
    /* Custom scrollbar for options */
    .options-scroll::-webkit-scrollbar {
        width: 4px;
    }
    .options-scroll::-webkit-scrollbar-track {
        background: transparent;
    }
    .options-scroll::-webkit-scrollbar-thumb {
        background: rgba(128, 128, 128, 0.3);
        border-radius: 4px;
    }
</style>
