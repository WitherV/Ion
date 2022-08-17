package net.horizonsend.ion.core.events

import net.starlegacy.feature.multiblock.Multiblock
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MultiblockDetectEvent(val player: Player, val multiblock: Multiblock) : Event(true) {
	override fun getHandlers(): HandlerList {
		return handlerList
	}

	companion object {
		@JvmStatic
		val handlerList = HandlerList()
	}
}