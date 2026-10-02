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
         * launch: some mods write their config while their mixins load, before PRO-Updater runs, and
         * those files must not make a fresh profile look used. A pack update is never a clean install:
         * once the state remembers a pack version, only [UPDATE] or [UP_TO_DATE] can come out.
         */
        fun detect(state: InstallState?, manifest: Manifest?, existedBefore: (String) -> Boolean): LaunchMode {
            if (manifest == null) return NO_MANIFEST
            val installed = state?.packVersion
            if (installed == null) {
                val anyConfig = manifest.payload().keys.any { it.startsWith("config/") && existedBefore(it) }
                return if (anyConfig) LEGACY_MIGRATION else CLEAN_INSTALL
            }
            // A downgrade is handled like an update too: adding missing files is always safe.
            return if (Versions.compare(manifest.version, installed) == 0) UP_TO_DATE else UPDATE
        }
    }
}
