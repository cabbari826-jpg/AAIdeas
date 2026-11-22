package com.aaideas.krossvord.model

/**
 * Proverb of the day: a well-known Azerbaijani proverb (folk, public domain) with one word
 * hidden; the player picks it from four. The hidden word is in [brackets]. Owner reviews.
 */
data class Proverb(val before: String, val answer: String, val after: String) {
    val full: String get() = before + answer + after
}

object Proverbs {
    val RAW = listOf(
        "Dost [dar] gündə tanınar.",
        "Damcı-damcı [göl] olar.",
        "Yeddi ölç, bir [biç].",
        "Dəmiri isti-isti [döyərlər].",
        "Tələsən [təndirə] düşər.",
        "Səbr ilə [halva] bişər.",
        "Bir əldən [səs] çıxmaz.",
        "Əl əli yuyar, əl də [üzü].",
        "Tikansız [gül] olmaz.",
        "İşləməyən [dişləməz].",
        "Ac toyuq yuxuda [darı] görər.",
        "Atalar sözü – ağlın [gözü].",
        "Bilməmək ayıb deyil, [öyrənməmək] ayıbdır.",
        "Dil [qılıncdan] itidir.",
        "Gec olsun, [güc] olmasın.",
        "Dağ dağa rast gəlməz, [insan] insana rast gələr.",
        "Su gələn [arxa] bir də gələr.",
        "Ayağını [yorğanına] görə uzat.",
        "Bir gül ilə [bahar] olmaz.",
        "Ehtiyat igidin [yaraşığıdır].",
        "İlan vuran ala [çatıdan] qorxar.",
        "İt hürər, [karvan] keçər.",
        "Ot kökü üstə [bitər].",
        "Xoş söz ilanı [yuvasından] çıxarar.",
        "Yaxşı at özünə [qamçı] vurdurmaz.",
        "Nə əkərsən, onu [biçərsən].",
        "Ağlamayan uşağa [süd] verməzlər.",
        "Min dost azdır, bir [düşmən] çox.",
        "Ağıl yaşda deyil, [başdadır].",
        "Dünya malı [dünyada] qalar.",
        "Haqq nazilər, amma [üzülməz].",
        "Söz var, kəsər savaşı, söz var, kəsdirər [başı].",
        "Elə gülmə ki, axırda [ağlayasan].",
        "Hər gözəlin bir [eybi] var.",
        "Ağac bar verəndə başını [aşağı] salar.",
        "Ağıllı düşmən ağılsız [dostdan] yaxşıdır.",
        "Yalançının evi yandı, heç kəs [inanmadı].",
        "Su axar, [çuxurunu] tapar.",
        "Qonaq qonağı istəməz, ev [sahibi] heç birini.",
        "Sən saydığını say, gör [fələk] nə sayır.",
        "Yaxşılıq et, at [dəryaya], balıq bilməsə, Xaliq bilər.",
        "Əkməsən, [biçməzsən].",
        "Var-dövlət əl [çirkidir].",
        "Gəlinim, sənə deyirəm, [qızım], sən eşit.",
        "Axşamın xeyrindən [sabahın] şəri yaxşıdır.",
    )

    val ALL: List<Proverb> = RAW.map {
        val a = it.indexOf('['); val b = it.indexOf(']')
        Proverb(it.substring(0, a), it.substring(a + 1, b), it.substring(b + 1))
    }

    fun forDay(day: Long): Proverb {
        val order = ALL.indices.shuffled(java.util.Random(1918L))
        return ALL[order[Math.floorMod(day, ALL.size.toLong()).toInt()]]
    }

    /** Four choices: the answer and three hidden words of other proverbs, in a fixed order for the day. */
    fun options(day: Long): List<String> {
        val p = forDay(day)
        val others = ALL.map { it.answer }.filter { it != p.answer }.distinct().shuffled(java.util.Random(day))
        return (others.take(3) + p.answer).shuffled(java.util.Random(day * 31 + 7))
    }
}
