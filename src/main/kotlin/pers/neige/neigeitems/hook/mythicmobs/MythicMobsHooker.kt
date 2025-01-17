package pers.neige.neigeitems.hook.mythicmobs

import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import pers.neige.neigeitems.NeigeItems
import pers.neige.neigeitems.event.MobInfoReloadedEvent
import pers.neige.neigeitems.event.MythicDropEvent
import pers.neige.neigeitems.event.MythicEquipEvent
import pers.neige.neigeitems.libs.bot.inker.bukkit.nbt.neigeitems.utils.WorldUtils
import pers.neige.neigeitems.manager.ConfigManager
import pers.neige.neigeitems.manager.HookerManager
import pers.neige.neigeitems.manager.HookerManager.mythicMobsHooker
import pers.neige.neigeitems.manager.ItemManager
import pers.neige.neigeitems.manager.ItemPackManager
import pers.neige.neigeitems.utils.ConfigUtils
import pers.neige.neigeitems.utils.ItemUtils
import pers.neige.neigeitems.utils.ItemUtils.copy
import pers.neige.neigeitems.utils.ItemUtils.getCaughtVelocity
import pers.neige.neigeitems.utils.ItemUtils.getNbt
import pers.neige.neigeitems.utils.ItemUtils.getNbtOrNull
import pers.neige.neigeitems.utils.ItemUtils.loadItems
import pers.neige.neigeitems.utils.ItemUtils.saveToSafe
import pers.neige.neigeitems.utils.PlayerUtils.setMetadataEZ
import pers.neige.neigeitems.utils.SchedulerUtils.async
import pers.neige.neigeitems.utils.SchedulerUtils.sync
import pers.neige.neigeitems.utils.SectionUtils.parseSection
import java.io.File
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * MM挂钩
 */
abstract class MythicMobsHooker {
    /**
     * MM挂钩版本
     */
    abstract val version: String

    /**
     * MM怪物信息
     */
    val mobInfos: ConcurrentHashMap<String, ConfigurationSection> = ConcurrentHashMap<String, ConfigurationSection>()

    /**
     * MM怪物生成事件
     */
    abstract val spawnEventClass: Class<out Event>

    /**
     * MM怪物死亡事件
     */
    abstract val deathEventClass: Class<out Event>

    /**
     * MM重载事件
     */
    abstract val reloadEventClass: Class<out Event>

    /**
     * MM怪物生成事件监听器, 监听器优先级HIGH, 得以覆盖MM自身的装备操作
     */
    abstract val spawnListener: Listener

    /**
     * MM怪物死亡事件监听器, 监听器优先级NORMAL
     */
    abstract val deathListener: Listener

    /**
     * MM重载事件监听器, 监听器优先级NORMAL
     */
    abstract val reloadListener: Listener

    /**
     * 获取是否存在对应ID的MM物品
     *
     * @param id MM物品ID
     * @return 是否存在对应ID的MM物品
     */
    abstract fun hasItem(id: String): Boolean

    /**
     * 获取MM物品, 不存在对应ID的MM物品则返回null
     *
     * @param id MM物品ID
     * @return MM物品(不存在则返回null)
     */
    abstract fun getItemStack(id: String): ItemStack?

    /**
     * 同步获取MM物品, 不存在对应ID的MM物品则返回null(在5.1.0左右的版本中, MM物品的获取强制同步)
     * 不一定真的同步获取, 只在必要时同步(指高版本)
     *
     * @param id MM物品ID
     * @return MM物品(不存在则为空)
     */
    abstract fun getItemStackSync(id: String): ItemStack?

    /**
     * 释放MM技能
     *
     * @param entity 技能释放者
     * @param skill 技能ID
     */
    abstract fun castSkill(entity: Entity, skill: String, trigger: Entity? = null)

    /**
     * 获取所有MM物品ID
     *
     * @return 所有MM物品ID
     */
    abstract fun getItemIds(): List<String>

    /**
     * 判断实体是否为MM生物
     *
     * @param entity 待判断实体
     * @return 该实体是否为MM生物
     */
    abstract fun isMythicMob(entity: Entity): Boolean

    /**
     * 获取MM实体的ID(非MM实体返回null)
     *
     * @param entity MM实体
     * @return MM实体ID(非MM实体返回null)
     */
    abstract fun getMythicId(entity: Entity): String?

    /**
     * 尝试从MM怪物出生事件或死亡事件中获取entity(非对应事件返回null)
     *
     * @param event MM怪物出生事件或死亡事件
     * @return 事件中的entity字段(非对应事件返回null)
     */
    abstract fun getEntity(event: Event): Entity?

    /**
     * 尝试从MM怪物死亡事件中获取killer(非对应事件返回null)
     *
     * @param event MM怪物死亡事件
     * @return 事件中的killer字段(非对应事件返回null)
     */
    abstract fun getKiller(event: Event): LivingEntity?

    /**
     * 尝试从MM怪物出生事件或死亡事件中获取MM怪物的internalName(非对应事件返回null)
     *
     * @param event MM怪物出生事件或死亡事件
     * @return MM怪物的internalName(非对应事件返回null)
     */
    abstract fun getInternalName(event: Event): String?

    /**
     * 尝试从MM怪物出生事件或死亡事件中获取MM怪物的mobLevel(非对应事件返回null)
     *
     * @param event MM怪物出生事件或死亡事件
     * @return MM怪物的mobLevel(非对应事件返回null)
     */
    abstract fun getMobLevel(event: Event): Double?

    /**
     * 将 Bukkit 实体转换为 MythicMobs 包了一层的 BukkitEntity.
     *
     * @param entity 待转换实体
     * @return BukkitEntity
     */
    abstract fun adapt(entity: Entity): Any

    private val df2 = DecimalFormat("#0.00")

    private fun getMobParams(entity: LivingEntity, internalName: String, mobLevel: Int): MutableMap<String, String> {
        return mutableMapOf<String, String>().also { map ->
            map["mobMaxHealth"] = df2.format(entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)!!.value)
            map["mobId"] = internalName
            map["mobLevel"] = mobLevel.toString()
            val location = entity.location
            map["mobLocationX"] = df2.format(location.x)
            map["mobLocationY"] = df2.format(location.y)
            map["mobLocationZ"] = df2.format(location.z)
            map["mobLocationYaw"] = df2.format(location.yaw)
            map["mobLocationPitch"] = df2.format(location.pitch)
            map["mobWorld"] = entity.world.name
            map["mobName"] = entity.name
            entity.customName?.let {
                map["mobCustomName"] = it
            }
        }
    }

    /**
     * 为MM怪物穿戴装备
     *
     * @param entity 怪物实体
     * @param internalName 怪物ID
     */
    fun spawnEvent(
        internalName: String, entity: LivingEntity, mobLevel: Int
    ) {
        // 获取MM怪物的ConfigurationSection
        val config = mobInfos[internalName] ?: return
        // 装备信息
        val equipment = config.getStringList("NeigeItems.Equipment")
        // 掉落装备概率
        val dropEquipment = config.getStringList("NeigeItems.DropEquipment")

        val entityEquipment = entity.equipment
        val dropChance = HashMap<String, Double>()

        // 构建怪物参数
        val params = getMobParams(entity, internalName, mobLevel)

        // 获取死亡后相应NI物品掉落几率
        for (value in dropEquipment) {
            val string = value.parseSection(params)
            var id = string.lowercase(Locale.getDefault())
            var chance = 1.toDouble()
            if (string.contains(" ")) {
                val index = string.indexOf(" ")
                id = string.substring(0, index).lowercase(Locale.getDefault())
                chance = string.substring(index + 1).toDoubleOrNull() ?: 1.toDouble()
            }
            dropChance[id] = chance
        }

        // 获取出生附带装备信息
        for (value in equipment) {
            val string = value.parseSection(params)
            if (!string.contains(": ")) continue
            val index = string.indexOf(": ")
            val slot = string.substring(0, index).lowercase(Locale.getDefault())
            val info = string.substring(index + 2)
            // [物品ID] (生成概率) (指向数据)
            val args = info.split(" ", limit = 3)

            val data: String? = args.getOrNull(2)

            if (args.size > 1) {
                val probability = args[1].toDoubleOrNull()
                if (probability != null && ThreadLocalRandom.current().nextDouble() > probability) continue
            }

            try {
                val itemStack = HookerManager.getNiOrHookedItem(args[0], data) ?: continue
                if (itemStack.type != Material.AIR) {
                    dropChance[slot]?.let { chance ->
                        val itemTag = itemStack.getNbt()
                        itemTag.putDeepDouble("NeigeItems.dropChance", chance)
                        itemTag.saveToSafe(itemStack)
                    }
                }
                val event = MythicEquipEvent(entity, internalName, slot, itemStack)
                if (!event.call()) continue
                when (slot) {
                    "helmet" -> {
                        entityEquipment?.helmet = event.itemStack
                    }

                    "chestplate" -> {
                        entityEquipment?.chestplate = event.itemStack
                    }

                    "leggings" -> {
                        entityEquipment?.leggings = event.itemStack
                    }

                    "boots" -> {
                        entityEquipment?.boots = event.itemStack
                    }

                    "mainhand" -> {
                        entityEquipment?.setItemInMainHand(event.itemStack)
                    }

                    "offhand" -> {
                        entityEquipment?.setItemInOffHand(event.itemStack)
                    }
                }
            } catch (error: Throwable) {
                ConfigManager.config.getString("Messages.equipFailed")?.let { message ->
                    logger.info(
                        message.replace("{mobID}", internalName).replace("{itemID}", args[0])
                    )
                }
                error.printStackTrace()
            }
        }
    }

    /**
     * 怪物死后进行掉落处理
     *
     * @param killer 击杀者
     * @param entity 被打死的怪物
     * @param internalName 怪物的MM生物ID
     * @param mobLevel 怪物的MM等级
     */
    fun deathEvent(
        killer: LivingEntity?, entity: LivingEntity, internalName: String, mobLevel: Int
    ) {
        // 获取MM怪物的ConfigurationSection
        val configSection = mobInfos[internalName] ?: return
        // 如果怪物未配置了NeigeItems相关信息则中止操作
        if (!configSection.contains("NeigeItems")) return

        val drops = configSection.getStringList("NeigeItems.Drops")
        val fishDrops = configSection.getStringList("NeigeItems.FishDrops")
        val dropPackRawIds = configSection.getStringList("NeigeItems.DropPacks")
        var offsetXString = configSection.getString("NeigeItems.FancyDrop.offset.x")
        var offsetYString = configSection.getString("NeigeItems.FancyDrop.offset.y")
        var angleType = configSection.getString("NeigeItems.FancyDrop.angle.type")

        // 东西都加载好了, 触发一下事件
        val configLoadedEvent = MythicDropEvent.ConfigLoaded(
            internalName,
            entity,
            killer,
            drops,
            fishDrops,
            dropPackRawIds,
            offsetXString,
            offsetYString,
            angleType
        )
        configLoadedEvent.call()
        if (configLoadedEvent.isCancelled) return

        // 判断玩家击杀
        if (killer !is Player && configSection.getBoolean("NeigeItems.PlayerOnly", true)) return

        val player = killer as? Player

        offsetXString = configLoadedEvent.offsetXString
        offsetYString = configLoadedEvent.offsetYString
        angleType = configLoadedEvent.angleType

        // 构建怪物参数
        val params = getMobParams(entity, internalName, mobLevel)

        // 预定掉落物列表
        val dropItems = ArrayList<ItemStack>()
        // 加载物品包掉落
        configLoadedEvent.dropPacks?.forEach { info ->
            // 物品包ID 数量 概率 指向数据
            val args = info.parseSection(params, player).split(" ", limit = 4)
            // 物品包ID
            val id = args[0]
            // 物品包数量
            val amount = args.getOrNull(1)?.let {
                if (it.contains("-")) {
                    val index = it.indexOf("-")
                    val min = it.substring(0, index).toIntOrNull()
                    val max = it.substring(index + 1, it.length).toIntOrNull()
                    if (min != null && max != null) {
                        ThreadLocalRandom.current().nextInt(min, max + 1)
                    } else {
                        null
                    }
                } else {
                    it.toIntOrNull()
                }
            } ?: 1
            // 生成概率
            val probability = args.getOrNull(2)?.toDoubleOrNull() ?: 1.0
            // 指向数据
            val data: String? = args.getOrNull(3)

            // 进行概率随机
            if (ThreadLocalRandom.current().nextDouble() <= probability) {
                // 获取对应物品包
                ItemPackManager.getItemPack(id)?.let { itemPack ->
                    // 尝试加载多彩掉落
                    if (itemPack.fancyDrop) {
                        offsetXString = itemPack.offsetXString
                        offsetYString = itemPack.offsetYString
                        angleType = itemPack.angleType
                    }
                    // 重复amount次
                    repeat(amount) {
                        // 加载物品掉落信息
                        dropItems.addAll(itemPack.getItemStacks(player, data))
                    }
                }
            }
        }
        // 掉落应该掉落的装备
        loadEquipmentDrop(entity, dropItems, player)
        // 加载掉落信息
        configLoadedEvent.drops?.let { loadItems(dropItems, it, player as? OfflinePlayer, params, null, true) }

        // 预定拟渔获掉落物列表
        val fishDropItems: ArrayList<ItemStack>? =
            // 存在击杀者, 载入fishDropItems
            if (killer != null) {
                ArrayList<ItemStack>().also {
                    configLoadedEvent.fishDrops?.let { fishDrops ->
                        loadItems(
                            it, fishDrops, player as? OfflinePlayer, params, null, true
                        )
                    }
                }
                // 不存在击杀者, 载入dropItems
            } else {
                configLoadedEvent.fishDrops?.let {
                    loadItems(
                        dropItems, it, null, params, null, true
                    )
                }
                null
            }

        // 物品都加载好了, 触发一下事件
        val dropEvent = MythicDropEvent.Drop(
            internalName, entity, player, dropItems, fishDropItems, offsetXString, offsetYString, angleType
        )
        dropEvent.call()
        if (dropEvent.isCancelled) return

        // 掉落物品
        ItemUtils.dropItems(
            dropEvent.dropItems,
            entity.location,
            killer,
            dropEvent.offsetXString,
            dropEvent.offsetYString,
            dropEvent.angleType
        )
        if (killer != null) {
            // 拟渔获向量计算
            val caughtVelocity = getCaughtVelocity(entity.location, killer.location)
            // 拟渔获掉落
            sync {
                dropEvent.fishDropItems?.forEach { itemStack ->
                    val nbt = itemStack.getNbtOrNull()
                    val neigeItems = nbt?.getCompound("NeigeItems")
                    // 记录掉落物拥有者
                    val owner = neigeItems?.getString("owner")
                    // 移除相关nbt, 防止物品无法堆叠
                    owner?.let {
                        neigeItems.remove("owner")
                        nbt.saveToSafe(itemStack)
                    }
                    // 记录掉落物拥有者
                    val hide = neigeItems?.getBoolean("hide")
                    // 掉落
                    WorldUtils.dropItem(entity.world, entity.location, itemStack) { item ->
                        // 设置拥有者相关Metadata
                        owner?.let {
                            item.setMetadataEZ("NI-Owner", it)
                        }
                        if (hide == true) {
                            item.addScoreboardTag("NI-Hide")
                        }
                        item.velocity = caughtVelocity
                        item.addScoreboardTag("NeigeItems")
                        // 掉落物技能
                        neigeItems?.getString("dropSkill")?.let { dropSkill ->
                            mythicMobsHooker?.castSkill(item, dropSkill, entity)
                        }
                    }
                }
            }
        }
    }

    init {
        loadMobInfos()
    }

    /**
     * 加载怪物配置
     */
    fun loadMobInfos() {
        async {
            mobInfos.clear()
            ConfigUtils.getAllFiles("MythicMobs", "Mobs").forEach(this::loadMobInfosFromMobFile)
            ConfigUtils.getAllFiles("MythicMobs", "mobs").forEach(this::loadMobInfosFromMobFile)
            loadMobInfosFromPackDir(
                File(
                    File(
                        NeigeItems.getInstance().dataFolder.parent, File.separator + "MythicMobs"
                    ), File.separator + "Packs"
                )
            )
            loadMobInfosFromPackDir(
                File(
                    File(
                        NeigeItems.getInstance().dataFolder.parent, File.separator + "MythicMobs"
                    ), File.separator + "packs"
                )
            )
            MobInfoReloadedEvent().call()
        }
    }

    private fun loadMobInfosFromMobFile(mobFile: File) {
        val config = YamlConfiguration.loadConfiguration(mobFile)
        config.getKeys(false).forEach { id ->
            config.getConfigurationSection(id)?.let {
                mobInfos[id] = it
            }
        }
    }

    private fun loadMobInfosFromPackDir(packDir: File) {
        if (!packDir.exists() || !packDir.isDirectory) return
        val packs = packDir.listFiles() ?: arrayOf<File>()
        for (packFile: File in packs) {
            if (!packFile.isDirectory) continue
            ConfigUtils.getAllFiles(File(packFile, "Mobs")).forEach(this::loadMobInfosFromMobFile)
            ConfigUtils.getAllFiles(File(packFile, "mobs")).forEach(this::loadMobInfosFromMobFile)
        }
    }

    /**
     * 根据掉落信息加载掉落物品
     *
     * @param entity 待掉落物品的MM怪物
     * @param dropItems 用于存储待掉落物品
     * @param player 用于解析物品的玩家
     */
    private fun loadEquipmentDrop(
        entity: LivingEntity, dropItems: ArrayList<ItemStack>, player: LivingEntity?
    ) {
        // 获取MM怪物身上的装备
        val entityEquipment = entity.equipment
        // 一个个的全掏出来, 等会儿挨个康康
        val equipments = ArrayList<ItemStack>()
        entityEquipment?.helmet?.copy()?.let { equipments.add(it) }
        entityEquipment?.chestplate?.copy()?.let { equipments.add(it) }
        entityEquipment?.leggings?.copy()?.let { equipments.add(it) }
        entityEquipment?.boots?.copy()?.let { equipments.add(it) }
        entityEquipment?.itemInMainHand?.copy()?.let { equipments.add(it) }
        entityEquipment?.itemInOffHand?.copy()?.let { equipments.add(it) }

        loadEquipmentDrop(equipments, dropItems, player)
    }

    /**
     * 根据掉落信息加载掉落物品
     *
     * @param equipments 怪物身上的装备
     * @param dropItems 用于存储待掉落物品
     * @param player 用于解析物品的玩家
     */
    private fun loadEquipmentDrop(
        equipments: ArrayList<ItemStack>, dropItems: ArrayList<ItemStack>, player: LivingEntity?
    ) {
        // 遍历怪物身上的装备, 看看哪个是生成时自带的需要掉落的NI装备
        for (itemStack in equipments) {
            // 空检测
            if (itemStack.type == Material.AIR) continue
            // 获取NBT
            val itemTag = itemStack.getNbtOrNull() ?: continue
            // 掏掉落概率
            val neigeItems = itemTag.getCompound("NeigeItems") ?: continue
            val dropChance = neigeItems.getDoubleOrNull("dropChance") ?: continue
            if (ThreadLocalRandom.current().nextDouble() <= dropChance) {
                val id = neigeItems.getStringOrNull("id")
                // 处理NI物品(根据玩家信息重新生成)
                if (id != null) {
                    val target = when (player) {
                        is OfflinePlayer -> player
                        else -> null
                    }
                    ItemManager.getItemStack(id, target, neigeItems.getStringOrNull("data"))?.let {
                        // 丢进待掉落列表里
                        dropItems.add(it)
                    }
                    // 处理MM/EI物品(单纯移除NBT)
                } else {
                    neigeItems.remove("dropChance")
                    if (neigeItems.isEmpty()) {
                        itemTag.remove("NeigeItems")
                    }
                    itemTag.saveToSafe(itemStack)
                    dropItems.add(itemStack)
                }
            }
        }
    }

    private companion object {
        @JvmStatic
        private val logger = LoggerFactory.getLogger(MythicMobsHooker::class.java.simpleName)
    }
}