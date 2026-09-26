package com.inmc.fishing.fish

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * 낚은 물고기 한 마리에 붙는 정보.
 *
 * 크기·등급·트로피 여부는 **아이템 자체에** 붙어 다닌다. 플러그인 쪽 목록에 들고 있으면
 * 아이템이 상자로, 다른 사람 손으로, 다른 서버로 옮겨가는 순간 그 목록과 갈라진다.
 *
 * **크기는 인벤토리 아이템에 표시하지 않는다** — 어망에서만 보인다. 2세대의 결정이고 그대로
 * 뒀다. 인벤토리 로어에는 트로피 여부만 붙는다.
 */
data class FishStamp(
    val fishId: String,
    val gradeId: String,
    val size: Double,
    val trophy: Trophy,
    /** 잡은 시각. 어망이 정렬과 표시에 쓴다. */
    val caughtAt: Long,
) {

    fun applyTo(stack: ItemStack) {
        stack.editMeta { meta ->
            val pdc = meta.persistentDataContainer
            pdc.set(Keys.FISH_ID, PersistentDataType.STRING, fishId)
            pdc.set(Keys.GRADE, PersistentDataType.STRING, gradeId)
            pdc.set(Keys.SIZE, PersistentDataType.DOUBLE, size)
            pdc.set(Keys.TROPHY, PersistentDataType.STRING, trophy.name)
            pdc.set(Keys.CAUGHT_AT, PersistentDataType.LONG, caughtAt)
        }
    }

    companion object {

        /**
         * 아이템에서 읽는다. 우리가 찍은 물고기가 아니면 null.
         *
         * 읽기는 `ItemStack.persistentDataContainer` 를 직접 쓴다 — `itemMeta` 를 통하면
         * 메타를 통째로 복제한다. 어망 정렬이나 인벤토리 훑기에서 아이템마다 부르는 경로다.
         */
        fun read(stack: ItemStack?): FishStamp? {
            if (stack == null || stack.type.isAir) return null
            val pdc = stack.persistentDataContainer
            val fishId = pdc.get(Keys.FISH_ID, PersistentDataType.STRING) ?: return null

            return FishStamp(
                fishId = fishId,
                gradeId = pdc.get(Keys.GRADE, PersistentDataType.STRING).orEmpty(),
                size = pdc.get(Keys.SIZE, PersistentDataType.DOUBLE) ?: 0.0,
                trophy = pdc.get(Keys.TROPHY, PersistentDataType.STRING)
                    ?.let { name -> Trophy.entries.firstOrNull { it.name == name } } ?: Trophy.NONE,
                caughtAt = pdc.get(Keys.CAUGHT_AT, PersistentDataType.LONG) ?: 0L,
            )
        }

        /** 우리 물고기인지만 싸게 확인한다. */
        fun isFish(stack: ItemStack?): Boolean {
            if (stack == null || stack.type.isAir) return false
            return stack.persistentDataContainer.has(Keys.FISH_ID, PersistentDataType.STRING)
        }
    }

    /**
     * PDC 키.
     *
     * 네임스페이스를 `infishing` 으로 **고정**한다. `NamespacedKey(plugin, …)` 를 쓰면 플러그인
     * 이름이 네임스페이스가 되는데, 2세대 이름은 `InMc-Fishing` 이고 새 이름은 `inmc-fishing`
     * 이라 **기존 서버에 돌아다니는 물고기가 전부 평범한 아이템이 된다.**
     */
    object Keys {

        internal const val NAMESPACE = "infishing"

        @Suppress("DEPRECATION")
        private fun key(name: String) = NamespacedKey(NAMESPACE, name)

        val FISH_ID: NamespacedKey = key("fish_id")
        val GRADE: NamespacedKey = key("grade")
        val SIZE: NamespacedKey = key("size")
        val TROPHY: NamespacedKey = key("trophy")
        val CAUGHT_AT: NamespacedKey = key("caught_at")
    }
}

/**
 * [FishStamp] 을 설정 파일에 적고 되읽는다.
 *
 * PDC 로 아이템에 찍는 것([FishStamp.applyTo])과 **다른 경로**다. 어망은 아이템이 아니라
 * 목록이라 PDC 를 쓸 수 없다.
 */
object FishStampYaml {

    fun save(stamp: FishStamp, section: ConfigurationSection) {
        section.set("fish", stamp.fishId)
        section.set("grade", stamp.gradeId)
        section.set("size", stamp.size)
        section.set("trophy", stamp.trophy.name)
        section.set("caught-at", stamp.caughtAt)
    }

    fun load(section: ConfigurationSection): FishStamp? {
        val fishId = section.getString("fish")?.takeIf { it.isNotBlank() } ?: return null
        return FishStamp(
            fishId = fishId.lowercase(),
            gradeId = section.getString("grade").orEmpty().lowercase(),
            size = section.getDouble("size", 0.0),
            trophy = runCatching { Trophy.valueOf(section.getString("trophy").orEmpty()) }
                .getOrDefault(Trophy.NONE),
            caughtAt = section.getLong("caught-at", 0L),
        )
    }
}

/**
 * 낚은 개체마다 다른 것을 로어 줄로 만든다.
 *
 * 정의에 적힌 설명([com.inmc.fishing.registry.Decor.lore])은 **모든 개체가 같고**, 이쪽은
 * 개체마다 다르다. 그래서 한 줄로 합치지 않고 뒤에 덧붙인다 — 합치면 45cm 짜리와 80cm 짜리가
 * 인벤토리에서 서로 다른 아이템이 되어 스택이 갈라지는 것까지는 맞지만, 정의를 고칠 때
 * 개체 정보까지 같이 고쳐야 한다.
 */
object FishLore {

    fun of(stamp: FishStamp, gradeTag: String): List<String> = buildList {
        if (gradeTag.isNotBlank()) add("<gray>등급: " + gradeTag + "</gray>")
        if (stamp.size > 0.0) add("<gray>크기: <white>" + stamp.size + "cm</white></gray>")
        when (stamp.trophy) {
            Trophy.NORMAL -> add("<gold>★ 트로피</gold>")
            Trophy.RARE -> add("<light_purple>★★ 레어 트로피</light_purple>")
            Trophy.NONE -> Unit
        }
    }
}
