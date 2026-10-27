package dev.skyglint.features

import dev.skyglint.core.SignalModule
import dev.skyglint.features.garden.*
import dev.skyglint.features.dungeon.DungeonModule
import dev.skyglint.features.elections.ElectionModule
import dev.skyglint.features.mining.*
import dev.skyglint.features.session.ServerClosingModule
import dev.skyglint.features.trevor.TrevorModule
import dev.skyglint.features.bestiary.BestiaryModule
import dev.skyglint.features.pets.PetLifecycleModule
import dev.skyglint.features.slayer.SlayerModule
import dev.skyglint.features.fishing.encounters.FishingEncounterModule
import dev.skyglint.features.fishing.bait.FishingBaitModule

/** The same source modules serve the live adapter, replay fixtures and isolated in-game previews. */
object ContextModules {
    fun create(clock: () -> Long): List<SignalModule> = listOf(GardenVisitorModule(clock), GardenPestModule(clock),
        GardenMilestoneModule(clock), GardenStatisticsModule(clock), JacobContestModule(clock), DungeonModule(clock), ElectionModule(clock),
        MiningContextModule(clock), MiningEventModule(clock), RandomMiningEventModule(clock), TemporaryOccurrenceModule(clock),
        DrillFuelModule(clock), NucleusActivityModule(clock), CurrencySignalModule(clock),
        ServerClosingModule(clock), TrevorModule(clock), BestiaryModule(clock), PetLifecycleModule(), SlayerModule(clock),
        FishingEncounterModule(clock), FishingBaitModule(clock))
}
