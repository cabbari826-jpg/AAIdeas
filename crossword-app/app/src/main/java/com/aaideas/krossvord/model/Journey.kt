package com.aaideas.krossvord.model

/** One stop on the road: a place in Azerbaijan with a short, well-known fact. */
data class City(val name: String, val icon: String, val fact: String, val lon: Float = 0f, val lat: Float = 0f)

/**
 * "Yol xəritəsi": the levels lead along a road through Azerbaijan. A new place opens every
 * [STEP] solved levels (any pack counts), with a short fact. Only real, widely known facts.
 */
object Journey {
    const val STEP = 8

    val CITIES = listOf(
        City("Bakı", "🏙", "Xəzərin sahilində. İçərişəhər və Qız qalası UNESCO irsidir.", 49.87f, 40.41f),
        City("Qobustan", "🏞", "Qayaüstü təsvirləri min illər əvvəl çəkilib, UNESCO irsidir.", 49.4f, 40.08f),
        City("Şamaxı", "🕌", "Qədim Şirvanın paytaxtı. Cümə məscidi ilə tanınır.", 48.64f, 40.63f),
        City("Lahıc", "⚒", "Dağ kəndi, misgər ustaları ilə məşhurdur.", 48.39f, 40.85f),
        City("Qəbələ", "🏔", "Qafqaz Albaniyasının qədim paytaxtı olub.", 47.85f, 40.98f),
        City("Şəki", "🏰", "Xan sarayının şəbəkə pəncərələri və Şəki halvası məşhurdur.", 47.17f, 41.19f),
        City("Zaqatala", "🌰", "Fındıq bağları və sıx meşələri ilə tanınır.", 46.65f, 41.63f),
        City("Gəncə", "📜", "Böyük şair Nizami Gəncəvinin vətəni.", 46.36f, 40.68f),
        City("Göygöl", "💧", "Kəpəz dağının uçqunundan yaranmış gözəl göl.", 46.32f, 40.41f),
        City("Şuşa", "🌺", "Qarabağın tacı, musiqi beşiyi. Xarıbülbül burada bitir.", 46.75f, 39.76f),
        City("Naxçıvan", "🕍", "Möminə Xatın türbəsi Əcəmi Naxçıvaninin əsəridir.", 45.41f, 39.21f),
        City("Lənkəran", "🍵", "Çay plantasiyaları və subtropik təbiəti ilə tanınır.", 48.85f, 38.75f),
        City("Quba", "🍎", "Alma bağları və xalçaları ilə məşhurdur.", 48.51f, 41.36f),
        City("Xınalıq", "⛰", "Dağların başında, ən qədim kəndlərdən biri.", 48.13f, 41.18f),
    )

    /** How many places are open after [solved] levels (Bakı is open from the start). */
    fun opened(solved: Int): Int = (1 + solved / STEP).coerceAtMost(CITIES.size)

    /** Levels still to solve before the next place opens, or null when all are open. */
    fun toNext(solved: Int): Int? = if (opened(solved) >= CITIES.size) null else STEP - solved % STEP
}
