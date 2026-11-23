package com.aaideas.krossvord.model

/** One stop on the road: a place in Azerbaijan with a short, well-known fact. */
data class City(val name: String, val icon: String, val fact: String)

/**
 * "Yol xəritəsi": the levels lead along a road through Azerbaijan. A new place opens every
 * [STEP] solved levels (any pack counts), with a short fact. Only real, widely known facts.
 */
object Journey {
    const val STEP = 8

    val CITIES = listOf(
        City("Bakı", "🏙", "Xəzərin sahilində. İçərişəhər və Qız qalası UNESCO irsidir."),
        City("Qobustan", "🏞", "Qayaüstü təsvirləri min illər əvvəl çəkilib, UNESCO irsidir."),
        City("Şamaxı", "🕌", "Qədim Şirvanın paytaxtı. Cümə məscidi ilə tanınır."),
        City("Lahıc", "⚒", "Dağ kəndi, misgər ustaları ilə məşhurdur."),
        City("Qəbələ", "🏔", "Qafqaz Albaniyasının qədim paytaxtı olub."),
        City("Şəki", "🏰", "Xan sarayının şəbəkə pəncərələri və Şəki halvası məşhurdur."),
        City("Zaqatala", "🌰", "Fındıq bağları və sıx meşələri ilə tanınır."),
        City("Gəncə", "📜", "Böyük şair Nizami Gəncəvinin vətəni."),
        City("Göygöl", "💧", "Kəpəz dağının uçqunundan yaranmış gözəl göl."),
        City("Şuşa", "🌺", "Qarabağın tacı, musiqi beşiyi. Xarıbülbül burada bitir."),
        City("Naxçıvan", "🕍", "Möminə Xatın türbəsi Əcəmi Naxçıvaninin əsəridir."),
        City("Lənkəran", "🍵", "Çay plantasiyaları və subtropik təbiəti ilə tanınır."),
        City("Quba", "🍎", "Alma bağları və xalçaları ilə məşhurdur."),
        City("Xınalıq", "⛰", "Dağların başında, ən qədim kəndlərdən biri."),
    )

    /** How many places are open after [solved] levels (Bakı is open from the start). */
    fun opened(solved: Int): Int = (1 + solved / STEP).coerceAtMost(CITIES.size)

    /** Levels still to solve before the next place opens, or null when all are open. */
    fun toNext(solved: Int): Int? = if (opened(solved) >= CITIES.size) null else STEP - solved % STEP
}
