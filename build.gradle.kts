plugins {
    id("inmc.paper-plugin")
}

group = "com.inmc.fishing"
version = "1.0.0"

inmc {
    paper = "26.2"
    pluginName = "inmc-fishing"
}

dependencies {
    // isTransitive = false 필수: WorldEdit 이 Guava/Gson 을 strictly 로 못박아 paper-api 와
    // 해석이 충돌한다 (가이드 함정 3). 컴파일에 필요한 건 이 넷의 클래스뿐이다.
    compileOnly(libs.worldguard.bukkit) { isTransitive = false }
    compileOnly(libs.worldguard.core) { isTransitive = false }
    compileOnly(libs.worldedit.bukkit) { isTransitive = false }
    compileOnly(libs.worldedit.core) { isTransitive = false }

    // PlaceholderExpansion 은 추상 클래스라 Proxy 로 못 만든다. compileOnly 가 필요하다.
    compileOnly(libs.placeholderapi) { isTransitive = false }
    compileOnly(libs.vault.api) { isTransitive = false }

    // 셰이딩 대상은 bStats 하나뿐이다.
    implementation(libs.bstats.bukkit)

    // MMOItems 는 100% 리플렉션 - core 의 훅이 대신 닿는다.
}

tasks.shadowJar {
    // bStats 는 relocate 가 필수다 (가이드 함정 4).
    relocate("org.bstats", "com.inmc.fishing.lib.bstats")
}
