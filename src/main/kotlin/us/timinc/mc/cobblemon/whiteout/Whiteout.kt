package us.timinc.mc.cobblemon.whiteout

import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent
import net.fabricmc.api.ModInitializer
import net.minecraft.server.network.ServerPlayerEntity
import org.apache.logging.log4j.core.jmx.Server

object Whiteout : ModInitializer {
    const val MOD_ID = "cobblemon-whiteout"

    override fun onInitialize() {
        CobblemonEvents.BATTLE_FAINTED.subscribe { handleBattleFainted(it) }
    }

    private fun handleBattleFainted(battleFaintedEvent: BattleFaintedEvent) {
        // Fetch victim
        val victimPokemon = battleFaintedEvent.killed
        val victimEntity = victimPokemon.entity ?: return
        val victimOwner = victimEntity.owner ?: return
        if (victimPokemon.actor.type != ActorType.PLAYER) return

        // Fetch killer
        val killerEntity = victimPokemon.facedOpponents.first().entity;


        if (victimPokemon.actor.pokemonList.all { it.health == 0 }) {
            // player kill
            if (killerEntity?.owner != null){
                val killerOwner = killerEntity.owner as ServerPlayerEntity
                victimOwner.damage(
                    victimOwner.world.damageSources.playerAttack(killerOwner),
                    Float.MAX_VALUE
                )
            }
            // wild kill
            else{
                victimOwner.kill()
            }

        }
    }
}