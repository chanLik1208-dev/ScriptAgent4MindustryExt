import mindustry.gen.*
import mindustry.net.*
import arc.util.*
import mindustry.mod.Plugin

class TeamPersistencePlugin : Plugin() {
    private val playerTeams = mutableMapOf<String, Team>()

    init {
        Events.on(PlayerJoin::class.java) { event ->
            loadPlayerTeam(event.player)
        }

        Events.on(PlayerLeave::class.java) { event ->
            savePlayerTeam(event.player)
        }

        Events.on(ServerLoadEvent::class.java) {
            Groups.player.each { player ->
                loadPlayerTeam(player)
            }
        }
    }

    private fun savePlayerTeam(player: Player) {
        playerTeams[player.uuid()] = player.team()
    }

    private fun loadPlayerTeam(player: Player) {
        player.team(playerTeams[player.uuid()] ?: Team.sharded)
    }
}

// 注册插件
TeamPersistencePlugin()
