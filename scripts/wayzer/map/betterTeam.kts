package wayzer.map

import arc.Events
import mindustry.core.NetServer
import mindustry.game.Team
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.CoreBlock.CoreBuild

name = "更好的队伍"

data class AssignTeamEvent(val player: Player, val group: Iterable<Player>, val oldTeam: Team?) : Event,
    Event.Cancellable {
    var team: Team? = oldTeam
        set(value) {
            field = value
            cancelled = true
        }
    override var cancelled: Boolean = false
    override val handler: Event.Handler get() = Companion

    companion object : Event.Handler()
}

val spectateTeam = Team.all[255]!!
val allTeam: Set<Team>
    get() {
        if (!state.rules.pvp) return setOf(state.rules.defaultTeam)
        return state.teams.getActive().mapTo(mutableSetOf()) { it.team }.apply {
            remove(Team.derelict)
            removeIf { !it.data().hasCore() }
            removeAll(bannedTeam)
        }
    }

var bannedTeam = emptySet<Team>()

onEnable {
    val backup = netServer.assigner
    netServer.assigner = NetServer.TeamAssigner { p, g ->
        randomTeam(p, g)
    }
    onDisable { netServer.assigner = backup }
    updateBannedTeam(true)
}
listen<EventType.PlayEvent> { updateBannedTeam(true) }

val savedTeams = mutableMapOf<String, Team>()
listen<EventType.ResetEvent> {
    bannedTeam = emptySet()
    savedTeams.clear()
}
listen<EventType.PlayerLeave> { savedTeams[it.player.uuid()] = it.player.team() }

//custom gameover
listen<EventType.BlockDestroyEvent> { e ->
    if (state.gameOver || !state.rules.pvp) return@listen
    if (e.tile.block() is CoreBlock)
        launch(Dispatchers.gamePost) {
            if (state.gameOver) return@launch
            allTeam.singleOrNull()?.let {
                state.gameOver = true
                Events.fire(EventType.GameOverEvent(it))
            }
        }
}
//fix 诈尸问题
listen<EventType.CoreChangeEvent> { e ->
    val team = e.core.team
    launch(Dispatchers.gamePost) {
        if (!team.active())
            Groups.build.filterIsInstance<CoreBuild>().forEach {
                if (it.lastDamage == team) it.lastDamage = Team.derelict
            }
    }
}

fun updateBannedTeam(force: Boolean = false) {
    if (force || bannedTeam.isEmpty())
        bannedTeam = state.rules.tags.get("@banTeam")?.split(',').orEmpty()
            .mapNotNull { Team.all.getOrNull(it.toIntOrNull() ?: -1) }.toSet()
    Groups.player.filter { it.team() in bannedTeam }.forEach {
        changeTeam(it)
        it.sendMessage("[yellow]因为原队伍被禁用,你已自动切换队伍".with(), MsgType.InfoMessage)
    }
}

fun randomTeam(player: Player, group: Iterable<Player> = Groups.player): Team {
    val allTeam = allTeam
    val bak = (savedTeams.remove(player.uuid()) ?: player.team().takeUnless { player.dead() })
        ?.takeIf { it in allTeam }
    val fromEvent = Dispatchers.game.safeBlocking { AssignTeamEvent(player, group, bak).emitAsync() }.team
    if (fromEvent != null) return fromEvent
    if (!state.rules.pvp) return state.rules.defaultTeam
    return allTeam.shuffled()
        .minByOrNull { group.count { p -> p.team() == it && player != p } }
        ?: state.rules.defaultTeam
}

fun changeTeam(p: Player, team: Team = randomTeam(p)) {
    p.clearUnit()
    p.team(team)
}

export(::changeTeam)
command("team", "管理指令: 修改自己或他人队伍(PVP模式)") {
    usage = "[队伍,不填列出] [玩家3位id,默认自己]"
    permission = "wayzer.ext.team.change"
    body {
        if (!state.rules.pvp) returnReply("[red]仅PVP模式可用".with())
        val team = arg.getOrNull(0)?.toIntOrNull()?.let { Team.get(it) } ?: let {
            val teams = allTeam.map { t -> "{id}({team.colorizeName}[])".with("id" to t.id, "team" to t) }
            returnReply("[yellow]可用队伍: []{list}".with("list" to teams))
        }
        val player = arg.getOrNull(1)?.let {
            depends("wayzer/user/shortID")?.import<(String) -> String?>("getUUIDbyShort")?.invoke(it)
                ?.let { id -> Groups.player.find { it.uuid() == id } }
                ?: returnReply("[red]找不到玩家,请使用/list查询正确的3位id".with())
        } ?: player ?: returnReply("[red]请输入玩家ID".with())
        changeTeam(player, team)
        broadcast(
            "[green]管理员更改了{player.name}[green]为{team.colorizeName}".with("player" to player, "team" to team)
        )
    }
}

PermissionApi.registerDefault("wayzer.ext.team.change", group = "@admin")