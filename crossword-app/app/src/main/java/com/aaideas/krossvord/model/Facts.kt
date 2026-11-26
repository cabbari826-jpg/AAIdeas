package com.aaideas.krossvord.model

/** "Bilirsinizmi?" facts shown after a solved level, one after another. Owner reviews them. */
object Facts {
    val ALL = listOf(
        "Bakı dünyanın dəniz səviyyəsindən ən aşağıda yerləşən paytaxtıdır.",
        "Xəzər dünyanın ən böyük qapalı su hövzəsidir.",
        "Azərbaycan əlifbasında 32 hərf var.",
        "1918-ci ildə qurulan Azərbaycan Xalq Cümhuriyyəti Şərqdə ilk demokratik respublika idi.",
        "İçərişəhər, Şirvanşahlar sarayı və Qız qalası 2000-ci ildə UNESCO siyahısına daxil edilib.",
        "Qobustanda 6000-dən çox qayaüstü təsvir var.",
        "Abşerondakı Yanardağ min illərdir sönmədən yanır: torpaqdan təbii qaz çıxır.",
        "Azərbaycan muğamı UNESCO-nun qeyri-maddi irs siyahısındadır.",
        "Azərbaycan xalçaçılıq sənəti 2010-cu ildə UNESCO siyahısına daxil edilib.",
        "Üzeyir Hacıbəylinin «Leyli və Məcnun» (1908) əsəri Şərqdə ilk operadır.",
        "Azərbaycanın ən yüksək zirvəsi Bazardüzüdür: 4466 metr.",
        "Kür Cənubi Qafqazın ən uzun çayıdır.",
        "Göygöl 1139-cu ildə zəlzələ zamanı Kəpəz dağının uçması ilə yaranıb.",
        "Dünyadakı 11 iqlim qurşağından 9-u Azərbaycanda var.",
        "Neft Daşları 1949-cu ildə açıq dənizdə tikilmiş ilk neft şəhəridir.",
        "1846-cı ildə Bibiheybətdə dünyada ilk dəfə mexaniki üsulla neft quyusu qazılıb.",
        "Nizami Gəncəvinin «Xəmsə»si beş poemadan ibarətdir.",
        "Şəki Xan sarayı bir dənə də mismar işlədilmədən tikilib.",
        "Qəbələ qədim Qafqaz Albaniyasının paytaxtı olub.",
        "İlk krossvord 1913-cü ildə Nyu-Yorkda qəzetdə çap olunub.",
        "Yetkin insanın bədənində 206 sümük var.",
        "Ahtapotun üç ürəyi var.",
        "Bal düzgün saxlananda heç vaxt xarab olmur.",
        "Zürafənin boynunda da insanınkı kimi 7 fəqərə var.",
        "Günəş işığı Yerə təxminən 8 dəqiqəyə çatır.",
        "Ay Yer ətrafında təxminən 27 gündə bir dövr edir.",
        "Kəpənəklər dadı ayaqları ilə bilir.",
        "Dəvəquşunun gözü beynindən böyükdür.",
        "Asiya dünyanın ən böyük qitəsidir.",
        "Sakit okean bütün qitələrin birlikdə sahəsindən böyükdür.",
        "Everest dünyanın ən hündür dağıdır: 8849 metr.",
        "Novruz bayramı 2009-cu ildə UNESCO siyahısına daxil edilib.",
        "Azərbaycan bayrağı ilk dəfə 1918-ci ildə qəbul edilib, 1991-ci ildə bərpa olunub.",
        "Məhəmməd Füzuli «Leyli və Məcnun» poemasını Azərbaycan dilində yazıb.",
        "Lənkəranda Azərbaycan çayının ən çox hissəsi yetişdirilir.",
        "Arı bir qaşıq bal üçün minlərlə çiçəyə qonur.",
        "Delfinlər yatanda beyinlərinin yarısı oyaq qalır.",
        "Qarğalar insan üzlərini illərlə yadda saxlayır.",
    )

    fun at(index: Int): String = ALL[Math.floorMod(index, ALL.size)]
}
