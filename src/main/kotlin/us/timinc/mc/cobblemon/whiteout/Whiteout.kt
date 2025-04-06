package us.timinc.mc.cobblemon.whiteout

// Lifesteal API Import
import mc.mian.lifesteal.api.PlayerImpl
// Cobblemon Imports
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent // Keep if you uncomment handleForfeit
import com.cobblemon.mod.common.api.events.battles.BattleStartedPreEvent
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.server
// import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent // Keep if you uncomment handleVictory
// import com.cobblemon.mod.common.entity.pokemon.PokemonEntity // Not directly used but good context

// Minecraft/Fabric Imports
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.impl.`object`.builder.FabricEntityTypeImpl.Builder.Living
import net.minecraft.entity.LivingEntity // Base type for owners
import net.minecraft.server.network.ServerPlayerEntity // Specific player type needed

// Logger Import
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Whiteout : ModInitializer {
    const val MOD_ID = "cobblemon-whiteout"
    private val LOGGER: Logger = LogManager.getLogger(MOD_ID)
    private val activePvpBattles: MutableMap<UUID, UUID> = ConcurrentHashMap()

    override fun onInitialize() {
        LOGGER.info("Initializing Cobblemon Whiteout ($MOD_ID)...")
        CobblemonEvents.BATTLE_FAINTED.subscribe { wildBattleWhiteout(it) }
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe { prepareWager(it) }
        CobblemonEvents.BATTLE_VICTORY.subscribe { handleVictory(it) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            handleAbandonment(handler.player)
        }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            handleAbandonment(entity);
        }
        LOGGER.info("Cobblemon Whiteout initialized.")
    }


    // Start Battle --> prepare wager (IF trainer battle)
    private fun prepareWager(battleStartedPreEvent: BattleStartedPreEvent){
        LOGGER.info("Player Start Length ${battleStartedPreEvent.battle.players.size}")

        if (battleStartedPreEvent.battle.players.size == 2) {
            val playerA = battleStartedPreEvent.battle.players[0]
            val playerB = battleStartedPreEvent.battle.players[1]

            // Cast to Lifesteal API PlayerImpl - Add error handling if cast fails
            val playerAImpl = playerA as? PlayerImpl
            val playerBImpl = playerB as? PlayerImpl

            if (playerAImpl != null && playerBImpl != null) {
                try {
                    LOGGER.info("Starting PvP wager between ${playerA.name.string} and ${playerB.name.string}")
                    val heartWager = playerA.suggestWager().coerceAtMost(playerB.suggestWager()); // choose minimum wager
                    playerAImpl.wager = heartWager;
                    playerBImpl.wager = heartWager;

                    // Track battle (needed for disconnect)
                    activePvpBattles[playerA.uuid] = playerB.uuid
                    activePvpBattles[playerB.uuid] = playerA.uuid
                    LOGGER.warn("Tracking active battle: ${playerA.uuid} vs ${playerB.uuid}")

                } catch (e: Exception) {
                    LOGGER.error("Failed to apply lifesteal wager for ${playerA.name.string} or ${playerB.name.string}: ${e.message}", e)
                }
            } else {
                // If Lifesteal isn't present or fails, don't track the battle for wager purposes.
                LOGGER.warn("Could not apply wager: One or both players could not be cast to PlayerImpl (Lifesteal API).")
                activePvpBattles.remove(playerA.uuid)
                activePvpBattles.remove(playerB.uuid)
            }
        }
    }

    // Win Battle --> Settle Wager (IF trainer battle)  [includes forfeits!]
    private fun handleVictory(battleVictoryEvent: BattleVictoryEvent) {
        val winnerActor = battleVictoryEvent.winners.filterIsInstance<PlayerBattleActor>().firstOrNull() ?: return
        val loserActor = battleVictoryEvent.losers.filterIsInstance<PlayerBattleActor>().firstOrNull() ?: return
        val winner = winnerActor.entity ?: return
        val loser = loserActor.entity ?: return

        // Remove from battle list
        val removedWinner = activePvpBattles.remove(winner.uuid)
        val removedLoser = activePvpBattles.remove(loser.uuid)
        // if users are not tracked, eject
        if (removedWinner == null || removedLoser == null) {
            LOGGER.debug("Battle victory between ${winner.name.string} and ${loser.name.string} detected, but it wasn't tracked for a wager.")
            return
        }

        // Settle Wagers (pay winner)
        LOGGER.info("Settling PvP wager for winner ${winner.name.string} against ${loser.name.string}")
        try {
            val winnerImpl = winner as? PlayerImpl
            if (winnerImpl != null) {
                // winner get their wager back + opponent's wager (wager times 2)
                val heartsWon = winnerImpl.wager * 2
                winnerImpl.gainHearts(heartsWon)
                LOGGER.debug("Awarded 4 hearts to ${winner.name.string}")
            } else {
                LOGGER.warn("Winner ${winner.name.string} could not be cast to PlayerImpl (Lifesteal API). Cannot award hearts.")
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to settle lifesteal wager for winner ${winner.name.string}: ${e.message}", e)
        }

    }

    // Abandons Battle --> Settle Wager, opponent wins (IF trainer battle)
    private fun handleAbandonment(abandoningPlayer: LivingEntity){
        val serverPlayer = abandoningPlayer as? ServerPlayerEntity ?: return
        handleAbandonment(serverPlayer)
    }
    private fun handleAbandonment(abandoningPlayer: ServerPlayerEntity) {
        val abandoningPlayerUUID = abandoningPlayer.uuid
        LOGGER.debug("Player ${abandoningPlayer.name.string} disconnected. Checking for active PvP battle.")

        // *** Check if the disconnected player was in a tracked battle and remove them ***
        val opponentUUID = activePvpBattles.remove(abandoningPlayerUUID)

        if (opponentUUID != null) {
            // They were in a battle! Remove the opponent's tracking entry as well.
            activePvpBattles.remove(opponentUUID)
            LOGGER.info("Player ${abandoningPlayer.name.string} disconnected during a tracked PvP battle against opponent UUID $opponentUUID. Settling wager.")

            // Find the opponent entity (who should still be online)
            val opponentPlayer = abandoningPlayer.server.playerManager.getPlayer(opponentUUID)

            if (opponentPlayer != null) {
                try {
                    val opponentImpl = opponentPlayer as? PlayerImpl
                    if (opponentImpl != null) {
                        // winner (opponent) get their wager back + opponent's wager (wager times 2)
                        val heartsWon = opponentImpl.wager * 2
                        opponentImpl.gainHearts(heartsWon)
                        LOGGER.info("Awarded 4 hearts to ${opponentPlayer.name.string} due to opponent disconnecting.")
                    } else {
                        LOGGER.warn("Opponent ${opponentPlayer.name.string} could not be cast to PlayerImpl (Lifesteal API). Cannot award hearts on disconnect.")
                    }
                } catch (e: Exception) {
                    LOGGER.error("Failed to settle lifesteal wager on disconnect for opponent ${opponentPlayer.name.string}: ${e.message}", e)
                }
            } else {
                LOGGER.warn("Opponent UUID $opponentUUID found for disconnected player ${abandoningPlayer.name.string}, but the opponent entity could not be found online. Wager lost.")
                // This scenario is less likely unless the opponent disconnected almost simultaneously.
            }
        } else {
            LOGGER.debug("Player ${abandoningPlayer.name.string} was not in a tracked PvP battle upon disconnect.")
        }
    }


    // Whiteout --> Minecraft Death (IF wild Pokémon battle)
    private fun wildBattleWhiteout(battleFaintedEvent: BattleFaintedEvent) {
        // --- Get Victim Details ---
        val victimPokemon = battleFaintedEvent.killed
        if (victimPokemon.actor.type != ActorType.PLAYER) return
        val victimOwner = victimPokemon.entity?.owner ?: return

        // Team Wipe (wild battle)
        if (victimPokemon.actor.pokemonList.all { it.health == 0 }) {
            val killerPokemon = victimPokemon.facedOpponents.firstOrNull()?.entity ?: return
            val killerOwner = killerPokemon.owner
            if ((killerOwner == null) && (victimOwner is ServerPlayerEntity)) {
                victimOwner.kill()
            }
        }
    }

}