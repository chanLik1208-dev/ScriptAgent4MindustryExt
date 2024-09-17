import mindustry.gen.Player
import mindustry.net.Administration

val playerTeams = mutableMapOf<String, Team>()

// 保存玩家队伍信息
fun savePlayerTeam(player: Player) {
    playerTeams[player.uuid()] = player.team()
}

// 恢复玩家队伍信息
fun restorePlayerTeam(player: Player) {
    player.team(playerTeams[player.uuid()] ?: Team.sharded)
}

// 监听玩家加入事件
Events.on(PlayerJoin::class.java) { event ->
    restorePlayerTeam(event.player)
}

// 监听玩家退出事件
Events.on(PlayerLeave::class.java) { event ->
    savePlayerTeam(event.player)
}

// 监听服务器重启事件
Events.on(ServerLoadEvent::class.java) {
    Groups.player.each { player ->
        restorePlayerTeam(player)
    }
}
