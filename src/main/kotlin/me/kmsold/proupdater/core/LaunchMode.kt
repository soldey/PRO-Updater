package me.kmsold.proupdater.core

enum class LaunchMode {
    /** No `defaults/manifest.json`: the mod runs outside the pack, only the player's own actions are done. */
    NO_MANIFEST,

    /** A new profile: the pack settings are laid out in full and the default steps applied. */
    CLEAN_INSTALL,

    /** A player coming from a pack version without PRO-Updater: handled like an update. */
    LEGACY_MIGRATION,

    /** The manifest is from another pack version than the last run: only missing files are added. */
    UPDATE,

    /** Same pack version as the last run. */
    UP_TO_DATE,
    ;

    companion object {

        /**
         * [existedBefore] answers for game-relative paths whether the file was there before this
         * launch. A profile counts as already set up only when more than half of the pack's
         * `config/` files were: some mods write their config while their mixins load, before any
         * pre-launch code, and Essential ends the very first launch to install its updates before
         * PRO-Updater gets to run, so a fresh profile can start with a handful of configs already
         * in place. A pack update is never a clean install: once the state remembers a pack version,
         * only [UPDATE] or [UP_TO_DATE] can come out.
         */
        fun detect(state: InstallState?, manifest: Manifest?, existedBefore: (String) -> Boolean): LaunchMode {
            if (manifest == null) return NO_MANIFEST
            val installed = state?.packVersion
            if (installed == null) {
                val configs = manifest.payload().keys.filter { it.startsWith("config/") }
                val existing = configs.count(existedBefore)
                return if (existing * 2 > configs.size) LEGACY_MIGRATION else CLEAN_INSTALL
            }
            // A downgrade is handled like an update too: adding missing files is always safe.
            return if (Versions.compare(manifest.version, installed) == 0) UP_TO_DATE else UPDATE
        }
    }
}
