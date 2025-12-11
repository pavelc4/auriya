<script>
    import { iconMap } from "../../lib/iconMap";

    export let name;
    export let className = "";

    $: icon = iconMap[name] || null;
</script>

{#if icon}
    <span class="inline-flex items-center justify-center {className}">
        <svelte:component this={icon} />
    </span>
{:else}
    <!-- Fallback or empty if icon not found -->
    <span class="hidden">Icon {name} not found</span>
{/if}
