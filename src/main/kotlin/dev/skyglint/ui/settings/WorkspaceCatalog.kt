package dev.skyglint.ui.settings

import dev.skyglint.config.ModConfig

/** Existing actions and new presentation preferences share one searchable, extendable catalog. */
class WorkspaceCatalog(private val actions: SettingsActions) {
    private val legacy = SettingsControls(actions).controls.associateBy { it.id }
    private fun existing(vararg ids: String) = ids.map(legacy::getValue)
    private fun toggle(id: String, title: String, hint: String, read: (ModConfig) -> Boolean,
                       write: (ModConfig, Boolean) -> ModConfig) = SettingsControl(id, title,
        state = { read(actions.config) }, hint = hint,
        action = { actions.changeConfig { write(it, !read(it)) } })
    private fun page(id: String, title: String, description: String, nav: Boolean = false, controls: List<SettingsControl>) =
        SettingsPage(id, title, description, nav) { controls }
    val pages: List<SettingsPage> = buildList {
        add(page("hud", "HUD layout", "Move and scale the complete adaptive row.", true, existing("enabled", "hud_scale", "top_offset", "reset_scale", "preview", "preview_scene")))
        add(page("modules", "Modules", "Choose what appears in your HUD.", true, emptyList()))
        add(page("events", "Events & rewards", "Choose which confirmations and event notices appear.", true, listOf(
            toggle("welcome", "Login greeting", "A short carousel after joining SkyBlock.", { it.showWelcome }, { c,v -> c.copy(showWelcome=v) }),
            toggle("event_reminders", "Event reminders", "Observed five-minute reminders. No persistent event banner.", { it.showEventReminders }, { c,v -> c.copy(showEventReminders=v) }),
            toggle("event_starts", "Event introductions", "Short start and login-active notices, including Jacob.", { it.showEventStarts }, { c,v -> c.copy(showEventStarts=v) }),
            toggle("skill_level_ups", "Skill level-ups", "Only explicitly confirmed server level-ups.", { it.showSkillLevelUps }, { c,v -> c.copy(showSkillLevelUps=v) }),
            toggle("pet_level_ups", "Pet level-ups", "Only explicitly confirmed pet level-ups.", { it.showPetLevelUps }, { c,v -> c.copy(showPetLevelUps=v) }),
            toggle("sb_level_ups", "SkyBlock level-ups", "Snapshots never create a celebration.", { it.showSkyBlockLevelUps }, { c,v -> c.copy(showSkyBlockLevelUps=v) })
        ) + existing("reward_mode")))
        add(page("appearance", "Appearance", "Transparent neutral material. Color carries information.", true, existing("hud_scale", "reset_scale") + listOf(
            SettingsControl("helper_limit", "Helper blocks", { actions.config.helperLimit.toString() }, hint="Maximum additional semantic blocks. Related counters stay grouped.", action={ actions.changeConfig { it.copy(helperLimit=it.helperLimit % 7 + 1) } }),
            SettingsControl("rotation", "Main-row rotation", { "${actions.config.rotationSeconds}s" }, hint="Time before another observed objective or boss UUID is shown.", action={ actions.changeConfig { it.copy(rotationSeconds=if(it.rotationSeconds>=15)4 else it.rotationSeconds+1) } })
        )))
        add(page("diagnostics", "Diagnostics", "Separate recording choices. Reports stay local.", true, existing("logging", "raw_text", "capture_chat", "diagnostics", "export_logs")))
        add(page("activity", "Activity HUD", "Bosses, objectives and your current activity.", controls=existing("enabled") + first { it.id=="appearance" }.controls(actions).filter { it.id in setOf("rotation","helper_limit") }))
        add(page("skills", "Skills & SkyBlock", "Skill XP, SkyBlock level and level-ups.", controls=existing("skill_hold") + first { it.id=="events" }.controls(actions).filter { it.id in setOf("skill_level_ups","sb_level_ups") }))
        add(page("pets", "Pets", "Your active companion and its progress.", controls=existing("show_pets") + listOf(
            toggle("pet_intro", "Pet switch introduction", "Briefly reveal the pet name after a confirmed equip.", { it.showPetIntroduction }, { c,v -> c.copy(showPetIntroduction=v) })
        ) + first { it.id=="events" }.controls(actions).filter { it.id=="pet_level_ups" }))
        add(page("mining", "Mining", "Event timers, drill fuel and mining helpers.", controls=listOf(
            toggle("mining_timers", "Mining event timers", "Introduction, then a compact observed event circle.", { it.showMiningTimers }, { c,v -> c.copy(showMiningTimers=v) }),
            toggle("fuel_warning", "Drill fuel warnings", "Additive below 50%; yellow below 25%, red below 10%, pulse below 5%.", { it.showFuelWarnings }, { c,v -> c.copy(showFuelWarnings=v) })
        )))
        add(page("garden", "Garden & Jacob", "Visitors, pests, crops and Jacob contests.", controls=listOf(
            toggle("visitors", "Visitors", "Only observed positive counts. Zero clears the helper.", { it.showGardenVisitors }, { c,v -> c.copy(showGardenVisitors=v) }),
            toggle("pests", "Pests", "Fresh sidebar and tab observations, with zero respected.", { it.showGardenPests }, { c,v -> c.copy(showGardenPests=v) }),
            toggle("milestones", "Crop milestones", "Keep crop progress separate from currency balances.", { it.showCropMilestones }, { c,v -> c.copy(showCropMilestones=v) })
        )))
        add(page("dungeon", "Dungeons", "Run phase, score, rank and secrets.", controls=existing("dungeon_detail")))
        add(page("fishing", "Fishing", "Catches, selected bait and remaining supplies.", controls=listOf(
            toggle("fishing_bait", "Selected bait", "Show the selected bait when the server preview is fresh.", { it.showFishingBait }, { c,v -> c.copy(showFishingBait=v) }),
            toggle("sack_warning", "Sack disabled helper", "A compact helper for an explicitly disabled bait sack.", { it.showSackWarning }, { c,v -> c.copy(showSackWarning=v) })
        )))
        add(page("rewards", "Rewards", "Drop notifications and individual item rules.", controls=existing("reward_mode")))
        add(page("buffs", "Cookie & God Potion", "Early warnings and buff expiry notices.", controls=listOf(
            toggle("cookie_warning", "Booster Cookie warnings", "Short warnings at observed 30, 10 and 1 minute remaining. Separate from inactivity notices.", { it.showCookieWarning }, { c,v -> c.copy(showCookieWarning=v) }),
            toggle("god_potion_warning", "God Potion warnings", "Short warnings at observed 30, 10 and 1 minute remaining; paused effects do not count.", { it.showGodPotionWarning }, { c,v -> c.copy(showGodPotionWarning=v) }),
            toggle("cookie_expiry", "Booster Cookie expiry", "Notify after the server confirms your Cookie Buff is no longer active.", { it.showCookieExpiry }, { c,v -> c.copy(showCookieExpiry=v) }),
            toggle("god_potion_expiry", "God Potion expiry", "Notify on confirmed inactivity on ordinary islands; paused effects do not count.", { it.showGodPotionExpiry }, { c,v -> c.copy(showGodPotionExpiry=v) })
        )))
        add(page("winter", "Jerry's Workshop", "Defense waves, gift piles and Reindrake.", controls=listOf(
            toggle("jerry_defense", "Jerry Defense", "Observed waves, remaining cubes and colored gift-pile indicators.", { it.showJerryDefense }, { c,v -> c.copy(showJerryDefense=v) }),
            toggle("jerry_results", "Defense results", "A short result after a confirmed server defense summary. Separate from the active defense HUD.", { it.showJerryResults }, { c,v -> c.copy(showJerryResults=v) }),
            toggle("reindrake", "Reindrake", "Represent recognized server bars. Unknown title variants keep their original text.", { it.showReindrake }, { c,v -> c.copy(showReindrake=v) })
        )))
        addAll(SettingsPageRegistry.pages())
    }
    companion object { val BUILTIN_IDS=setOf("hud","modules","events","appearance","updates","diagnostics","activity","skills","pets","mining","garden","dungeon","fishing","rewards","buffs","winter") }
}
