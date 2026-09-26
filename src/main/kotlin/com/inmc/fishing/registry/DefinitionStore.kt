package com.inmc.fishing.registry

import com.inmc.fishing.Fishing
import kr.inmc.core.integration.ItemRoles
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import kr.inmc.core.store.YamlFileStore
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack

/**
 * `<이름>.yml` 한 파일에 담긴 정의 목록.
 *
 * 물고기·낚싯대·미끼가 **같은 모양**이다 — 아이템 정체를 core 의 [StoredItem] 으로 들고,
 * id 로 찾고, 손에 든 스택이 무엇인지 되묻는다. 세 벌을 따로 쓰면 세 벌 다 고쳐야 하는
 * 일이 생기므로 공통부만 여기 둔다. 필드와 저장 형식은 전부 하위 클래스가 정한다.
 *
 * [identify] 는 **핫 패스**다 — 던질 때마다(낚싯대·미끼), 낚을 때마다(물고기) 불린다.
 * 그래서 재질로 먼저 거른다. 등록이 수백 개라도 후보는 보통 0~1 개로 줄어든다.
 *
 * ## 커스텀아이템이 있으면 ([hub])
 * - [role] 이 있는 목록(낚싯대·미끼·물약·생선살)은 **통째로 커스텀아이템 역할에서** 온다. 이 파일은 처음 한 번 옮기고
 *   `.migrated` 로 바꿔 둔 뒤 다시 쓰지 않는다([retired]). 넣고 빼기([put]·[remove])는 역할로 간다 — 낚시 관리 화면에서 고쳐도
 *   커스텀아이템 화면에서 고쳐도 같은 곳이 바뀐다. id 는 역할 값의 `id` 로 지킨다(다른 곳의 참조가 안 끊기게).
 * - [appearanceRole] 이 있는 목록(물고기)은 정의는 이 파일에 두고 **아이템(겉모습)만** 역할을 맡은 커스텀아이템으로.
 * - 옮기기 전의 옛 아이템(바닐라·수제)도 [legacies] 로 계속 알아본다.
 */
abstract class DefinitionStore<T : Any>(
    protected val fishing: Fishing,
    private val fileName: String,
    header: String,
    what: String,
    /** YAML 최상위 키. 예: `fish` · `rods` · `baits`. */
    private val rootKey: String,
) : YamlFileStore(fishing.io, listOf(fileName), header, what) {

    private val byId = LinkedHashMap<String, T>()
    private val byMaterial = HashMap<Material, MutableList<T>>()

    /** id → 옮기기 전의 아이템. [identify] 가 같이 본다. */
    private val legacies = HashMap<String, StoredItem>()

    /** 통째로 커스텀아이템 역할로 가는 목록의 역할 열쇠. null 이면 아니다. */
    protected open val role: String? = null

    /** 겉모습만 역할로 가는 목록(물고기)의 역할 열쇠. 역할 값 `fish` 가 이 목록의 id. */
    protected open val appearanceRole: String? = null

    /** 겉모습을 갈아끼운 사본. [appearanceRole] 을 쓰는 목록이 구현한다. */
    protected open fun withItem(value: T, item: StoredItem): T = value

    private val file get() = fishing.io.file(fileName)

    /** 커스텀아이템이 역할 창구를 꽂았고 이 목록이 연동하는 것이다. */
    val hub: Boolean get() = (role != null || appearanceRole != null) && ItemRoles.active

    /** 이미 커스텀아이템으로 옮겼다 — 이 파일은 다시 쓰지도 배포 기본값을 깔지도 않는다. */
    val retired: Boolean get() = role != null && ItemRoles.isRetired(file)
    protected abstract fun idOf(value: T): String

    protected abstract fun itemOf(value: T): StoredItem

    protected abstract fun read(key: String, section: ConfigurationSection): T?

    protected abstract fun write(value: T, section: ConfigurationSection)

    /** 파생 색인을 다시 만든다. 물고기는 등급별 묶음을 여기서 새로 만든다. */
    protected open fun reindex() {}

    // --- 조회 -------------------------------------------------------------------

    val size: Int get() = byId.size

    fun all(): List<T> = byId.values.toList()

    fun get(id: String?): T? = id?.let { byId[it.lowercase()] }

    fun exists(id: String?): Boolean = get(id) != null

    fun ids(): List<String> = byId.keys.toList()

    /**
     * 이 스택이 어떤 정의인지. 아니면 null.
     *
     * 같은 재질에 여럿이 걸려 있으면 **먼저 등록된 것이 이긴다.** 등록 순서가 곧 우선순위다.
     */
    fun identify(stack: ItemStack?): T? {
        if (stack == null || stack.type.isAir) return null
        val candidates = byMaterial[stack.type] ?: return null
        return candidates.firstOrNull { fishing.itemMatcher.matches(stack, itemOf(it)) || legacies[idOf(it)]?.let { old -> fishing.itemMatcher.matches(stack, old) } == true }
    }

    /** 이 정의가 씌우는 이름과 설명. 비어 있으면 원본 모습을 건들지 않는다. */
    protected open fun decorOf(value: T): Decor = Decor.NONE

    /**
     * 지급용 스택. 참조를 풀 수 없으면 null.
     *
     * [decorOf] 가 비어 있지 않으면 이름과 설명을 씌운다 — 바닐라 참조는 꾸며지 않은
     * 아이템을 돌려주기 때문이다.
     */
    fun stack(value: T, amount: Int = 1, extra: List<String> = emptyList()): ItemStack? {
        val stack = fishing.itemResolver.create(itemOf(value), amount) ?: return null
        if (isOurs(itemOf(value))) {
            // 커스텀아이템의 겉모습(이름·툴팁)은 그대로 두고 낚시가 붙이는 줄(크기·등급 …)만 덧붙인다.
            if (extra.isNotEmpty()) stack.editMeta { meta -> meta.lore(meta.lore().orEmpty() + extra.map { kr.inmc.core.util.Text.renderFlat(it) }) }
            return stack
        }
        decorOf(value).applyTo(stack, extra)
        return stack
    }

    private fun isOurs(item: StoredItem) = (item.ref as? ItemRef.Namespaced)?.namespace.equals("inmc", ignoreCase = true)

    // --- 변경 -------------------------------------------------------------------

    /** 새로 넣거나 같은 id 를 갈아끼운다. 커스텀아이템이 있으면 역할로(커스텀아이템이 아니면 만들어서). */
    fun put(value: T) {
        val role = role
        if (role != null && hub) {
            val item = itemOf(value)
            val ref = (item.ref as? ItemRef.Namespaced)?.takeIf { isOurs(item) }
                ?: stack(value, 1)?.let { ItemRoles.adopt(it, idOf(value)) }
            if (ref != null) {
                ItemRoles.assign(ref, role, ItemRoles.withLegacy(values(value), legacies[idOf(value)] ?: item.takeIf { !isOurs(it) }))
                return
            }
        }
        index(value)
        reindex()
        if (!retired) markDirty()
    }

    fun remove(id: String): Boolean {
        val removed = byId[id.lowercase()] ?: return false
        val role = role
        val ref = itemOf(removed).ref as? ItemRef.Namespaced
        if (role != null && hub && ref != null && isOurs(itemOf(removed))) {
            ItemRoles.assign(ref, role, null)
            return true
        }
        byId.remove(id.lowercase())
        byMaterial[itemOf(removed).material]?.remove(removed)
        reindex()
        if (!retired) markDirty()
        return true
    }

    private fun index(value: T) {
        byId.put(idOf(value), value)?.let { old ->
            byMaterial[itemOf(old).material]?.remove(old)
            legacies[idOf(old)]?.let { byMaterial[it.material]?.remove(old) }
        }
        byMaterial.getOrPut(itemOf(value).material) { ArrayList(2) }.add(value)
        legacies[idOf(value)]?.takeIf { it.material != itemOf(value).material }?.let { byMaterial.getOrPut(it.material) { ArrayList(2) }.add(value) }
    }

    // --- 커스텀아이템 ------------------------------------------------------------------

    /** 정의 → 역할 값(아이템 정체·겉모습 빼고, id 는 지킨다). */
    private fun values(value: T): Map<String, String> {
        val yaml = YamlConfiguration()
        write(value, yaml)
        return FishingRoles.flatten(yaml) + ("id" to idOf(value))
    }

    /** 역할이 바뀌었다(core `ItemRoles.listen`). 이 목록의 것이면 다시 짓는다. 처음 꽂혔고 파일이 남아 있으면 옮긴다. */
    fun onRolesChanged(changed: String?) {
        if (changed != null && changed != role && changed != appearanceRole) return
        when {
            role != null && hub && file.isFile -> load()
            role != null && hub -> rebuild()
            appearanceRole != null && hub -> { applyAppearance(); reindex() }
            else -> load()
        }
    }

    /** 역할을 맡은 아이템들로 목록을 새로 짓는다. */
    private fun rebuild() {
        val role = role ?: return
        byId.clear()
        byMaterial.clear()
        legacies.clear()
        for (holder in ItemRoles.holders(role)) {
            val id = holder.values["id"]?.lowercase()?.takeIf { it.isNotBlank() } ?: holder.ref.id
            if (byId.containsKey(id)) continue
            val section = FishingRoles.unflatten(holder.values)
            holder.item().save(section)
            section.set("id", id)
            val value = read(id, section) ?: continue
            holder.legacy()?.let { legacies[id] = it }
            index(value)
        }
        reindex()
    }

    /** 파일의 정의를 커스텀아이템으로 옮기고 파일은 `.migrated` 로. 겉모습(이름·설명)은 꾸민 채로 옮긴다. */
    private fun migrate(loaded: List<T>) {
        val role = role ?: return
        val target = ItemRoles.retire(file)
        var moved = 0
        for (value in loaded) {
            val stack = fishing.itemResolver.create(itemOf(value), 1)?.also { decorOf(value).applyTo(it) }?.let { ItemRoles.sample(it, itemOf(value)) }
            val ref = stack?.let { ItemRoles.adopt(it, idOf(value)) }
            if (ref == null) {
                fishing.logger.warning("'${idOf(value)}' 을(를) 커스텀아이템으로 옮기지 못했습니다 — ${target.name} 에 남아 있습니다")
                continue
            }
            ItemRoles.assign(ref, role, ItemRoles.withLegacy(values(value), itemOf(value).takeIf { !isOurs(it) }))
            moved++
        }
        fishing.logger.info("${fileName} 의 ${moved}개를 커스텀아이템으로 옮겼습니다 (원본: ${target.name})")
    }

    /** 물고기: 파일의 옛 겉모습을 커스텀아이템으로 옮기고(처음 한 번), 역할을 맡은 아이템으로 겉모습을 갈아끼운다. */
    private fun applyAppearance() {
        val role = appearanceRole ?: return
        var moved = false
        for (value in byId.values.toList()) {
            val item = itemOf(value)
            if (isOurs(item) || ItemRoles.holders(role).any { it.values["fish"] == idOf(value) }) continue
            val stack = fishing.itemResolver.create(item, 1)?.also { decorOf(value).applyTo(it) }?.let { ItemRoles.sample(it, item) } ?: continue
            val ref = ItemRoles.adopt(stack, idOf(value)) ?: continue
            ItemRoles.assign(ref, role, ItemRoles.withLegacy(mapOf("fish" to idOf(value)), item))
            moved = true
        }
        legacies.clear()
        for (holder in ItemRoles.holders(role)) {
            val id = holder.values["fish"]?.lowercase() ?: continue
            val value = byId[id] ?: continue
            holder.legacy()?.let { legacies[id] = it }
            index(withItem(value, holder.item()))
        }
        // 파일에는 커스텀아이템 참조를 적어 둔다 — 다음 시작에 다시 옮기지 않게.
        if (moved) markDirty()
    }

    // --- 영속화 (둘 다 메인 스레드에서 돈다) ------------------------------------------

    final override fun read(config: YamlConfiguration) {
        byId.clear()
        byMaterial.clear()
        val loaded = ArrayList<T>()
        config.getConfigurationSection(rootKey)?.let { root ->
            for (key in root.getKeys(false)) {
                val section = root.getConfigurationSection(key) ?: continue
                val value = read(key.lowercase(), section)
                if (value == null) {
                    fishing.logger.warning("'$key' 을(를) 읽지 못했습니다 - item 값을 확인해주세요")
                    continue
                }
                loaded += value
            }
        }
        if (role != null && hub) {
            if (file.isFile && loaded.isNotEmpty()) migrate(loaded) else if (file.isFile) ItemRoles.retire(file)
            rebuild()
            return
        }
        legacies.clear()
        for (value in loaded) index(value)
        if (appearanceRole != null && hub) applyAppearance()
        reindex()
    }

    final override fun write(config: YamlConfiguration) {
        val root = config.createSection(rootKey)
        for (value in byId.values) write(value, root.createSection(idOf(value)))
    }
}
