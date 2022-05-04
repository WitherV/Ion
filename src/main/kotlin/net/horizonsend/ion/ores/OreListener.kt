package net.horizonsend.ion.ores

import kotlin.random.Random
import net.horizonsend.ion.Ion
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.data.BlockData
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.persistence.PersistentDataType

internal class OreListener(private val plugin: Ion) : Listener {
	init {
		plugin.server.pluginManager.registerEvents(this, plugin)
	}

	private val oreCheckNamespace = NamespacedKey(plugin, "oreCheck")

	@EventHandler
	fun onChunkLoad(event: ChunkLoadEvent) {
		when (event.chunk.persistentDataContainer.get(oreCheckNamespace, PersistentDataType.INTEGER)) {
			2 -> return // Ores are up-to-date.
			null -> placeOres(event.chunk) // Ores have not been placed.
			else -> placeOres(event.chunk, true) // Ores are out of date.
		}
	}

	private class BlockLocation(var x: Int, var y: Int, var z: Int)

	private fun placeOres(chunk: Chunk, removeExisting: Boolean = false) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
			val chunkSnapshot = chunk.getChunkSnapshot(false, false, false)
			val placementConfiguration =
				OrePlacementConfig.values().find { it.name == chunkSnapshot.worldName } ?: return@Runnable
			val random = Random(chunk.chunkKey)

			val placedBlocks = mutableMapOf<BlockLocation, BlockData>()
			val placedOres = mutableMapOf<BlockLocation, Ore>()

			if (removeExisting) {
				val file =
					plugin.dataFolder.resolve("ores/${chunkSnapshot.worldName}/${chunkSnapshot.x}_${chunkSnapshot.z}.ores.csv")

				if (file.exists()) {
					file.readText().split("\n").forEach { oreLine ->
						val oreData = oreLine.split(",")

						val x = oreData[0].toInt()
						val y = oreData[1].toInt()
						val z = oreData[2].toInt()
						val original = Material.valueOf(oreData[3])
						val placedOre = Ore.valueOf(oreData[4])

						if (chunkSnapshot.getBlockType(x, y, z) == original)
							placedBlocks[BlockLocation(x, y, z)] = placedOre.blockData
					}
				}
			}

			for (sectionY in chunk.world.minHeight.shr(16)..chunk.world.maxHeight.shr(16)) {
				if (chunkSnapshot.isSectionEmpty(sectionY)) continue

				for (x in 0..15) for (y in 0..15) for (z in 0..15) {
					val blockData = chunkSnapshot.getBlockData(x, y + sectionY.shl(16), z)

					if (blockData.material.isAir) continue
					if (blockData.material.isInteractable) continue

					if (chunkSnapshot.getBlockType(x + 1, y, z).isAir) continue
					if (chunkSnapshot.getBlockType(x - 1, y, z).isAir) continue
					if (chunkSnapshot.getBlockType(x, y + 1, z).isAir) continue
					if (chunkSnapshot.getBlockType(x, y - 1, z).isAir) continue
					if (chunkSnapshot.getBlockType(x, y, z + 1).isAir) continue
					if (chunkSnapshot.getBlockType(x, y, z - 1).isAir) continue

					placementConfiguration.options.forEach { (ore, chance) ->
						if (random.nextDouble(0.0, 100.0) > 0.3 * (1 / chance)) return@forEach
						placedOres[BlockLocation(x, y, z)] = ore
					}
				}
			}

			placedBlocks.putAll(placedOres.map { Pair(it.key, it.value.blockData) })

			Bukkit.getScheduler().runTask(plugin, Runnable {
				placedOres.forEach { (position, ore) ->
					chunk.getBlock(position.x, position.y, position.z).setBlockData(ore.blockData, false)
				}

				chunk.persistentDataContainer.set(oreCheckNamespace, PersistentDataType.INTEGER, 2)
			})

			plugin.dataFolder.resolve("ores/${chunkSnapshot.worldName}")
				.apply { mkdirs() }
				.resolve("${chunkSnapshot.x}_${chunkSnapshot.z}.ores.csv")
				.writeText(placedOres.map {
					"${it.key.x},${it.key.y},${it.key.z},${chunkSnapshot.getBlockType(it.key.x, it.key.y, it.key.z)},${it.value}"
				}.joinToString("\n", "", ""))
		})
	}
}