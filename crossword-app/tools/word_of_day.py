"""Word of the day list: hard words with a description. Writes assets/word_of_day.json.
Drafts by Claude; the owner reviews. Words must not be answers or key words in any pack."""
import glob, json, os, sys
WORDS = [
("KƏHKƏŞAN", "Gecə səmanı ağ zolaq kimi kəsən saysız ulduzlar toplusu"),
("ŞƏFƏQ", "Günəş doğmazdan və batandan sonra üfüqdəki qızartı"),
("İLĞIM", "İsti səhrada uzaqdan su kimi görünən yalançı mənzərə"),
("ÇƏRŞƏNBƏ", "Novruzdan əvvəlki dörd axşamın hər biri: Su, Od, Yel, Torpaq"),
("XƏZAN", "Payızda yarpaqların saralıb tökülməsi; solğunluq"),
("ƏNDAZƏ", "Ölçü, hədd. Bunu bilməyən həddini aşar"),
("ZÜLMƏT", "Göz-gözü görməyən zil qaranlıq"),
("MƏRHƏMƏT", "Günahkara yazığı gəlib onu bağışlamaq hissi"),
("KİMYAGƏR", "Misi qızıla çevirməyə çalışan orta əsr alimi"),
("TƏNƏFFÜS", "Həm nəfəs alıb-vermə, həm də dərslər arasındakı fasilə"),
("SÜKUT", "Heç bir səs gəlməyən tam səssizlik"),
("HƏSRƏT", "Uzaqdakı birini görmək üçün çəkilən dərin, ağrılı istək"),
("MİZRAB", "Tar çalarkən barmaqlar arasında tutulan kiçik buynuz lövhə"),
("XONÇA", "Elçilikdə və Novruzda şirniyyatla bəzənən məcməyi"),
("BUXARI", "Otağın divarına hörülmüş açıq odlu ocaq"),
("ŞƏBƏKƏ", "Şəkidə mismarsız yığılan rəngli şüşəli taxta pəncərə"),
("TƏLAŞ", "Tələsik həyəcan, nə edəcəyini bilməmək halı"),
("İZDİHAM", "Bir yerə yığışmış çox böyük insan kütləsi"),
("MÜCRÜ", "Qiymətli xırdalıqlar saxlanan kiçik bəzəkli qutu"),
("ÇİLLƏ", "Qışın ən sərt qırx günü: böyük və kiçik olur"),
("ALAÇIQ", "Köçərilərin keçədən qurduğu dəyirmi çadır"),
("KƏCAVƏ", "Dəvə üstündə qadınlar üçün qurulan örtülü oturacaq"),
("SAPAND", "Rezinlə daş atmaq üçün uşaq oyuncağı"),
("NƏLBƏKİ", "Fincanın altına qoyulan kiçik boşqab"),
("QƏNDAN", "Süfrədə qənd və ya şəkər qoyulan qab"),
("ARMUDU", "Belinə görə adını bir meyvədən almış çay stəkanı"),
("SƏMƏNDƏR", "Odun içində yaşadığına inanılan əfsanəvi varlıq"),
("SİMURQ", "Şərq əfsanələrində dağ zirvəsində yaşayan nəhəng quş"),
("QARAVƏLLİ", "Bayramlarda oynanılan zarafatlı xalq səhnəciyi"),
("KEÇƏL", "Nağıllarda saçı olmayan, amma hamını aldadan hiyləgər qəhrəman"),
("ƏRƏSAT", "Qiyamət günü insanların toplaşacağı yer; böyük qarışıqlıq"),
("TƏVƏKKÜL", "Nəticəni taleyə buraxıb riskli işə girişmək"),
("ƏNBƏR", "Balinadan alınan və ətirçilikdə işlədilən qiymətli maddə"),
("MƏRMƏR", "Heykəltəraşların sevdiyi damarlı, cilalanan daş"),
("SƏDƏF", "Tarı və qutuları bəzəmək üçün işlədilən parlaq qabıq"),
("TİLSİM", "Sehrli qüvvəsi olduğuna inanılan əşya və ya söz"),
("ZƏRRƏBİN", "Gözlə görünməyəni böyüdüb göstərən laboratoriya cihazı"),
("DÜRBÜN", "Uzağı yaxın göstərən iki borulu cihaz"),
("PƏRGAR", "Dairə çəkmək üçün iki ayaqlı alət"),
("XƏTKEŞ", "Düz xətt çəkmək və ölçmək üçün lövhə"),
("KÜLƏŞ", "Biçilmiş taxılın quru gövdəsi, saman"),
("XIRMAN", "Taxılın döyülüb dəninin ayrıldığı meydança"),
("DƏYİRMAN", "Dəni una çevirən su və ya külək tikilisi"),
("ÇƏLƏNG", "Gül və yarpaqdan hörülmüş dairə"),
("MİRAS", "Nəsildən-nəslə keçən əmlak, adət və ya bilik"),
("SƏLTƏNƏT", "Hökmdarın taxt-tacı və hakimiyyət dövrü"),
("ÜFÜQ", "Yerlə göyün birləşdiyi kimi görünən xətt"),
("MÜƏMMA", "Həlli çox çətin sirr, tapmaca"),
("LABİRİNT", "Çıxışını tapmaq çətin olan dolaşıq yollar şəbəkəsi"),
("ƏFSANƏ", "Xalq arasında yaranmış möcüzəli rəvayət"),
("QƏRİBSƏMƏK", "Doğma yerdən uzaqda darıxmaq, özünü yad hiss etmək"),
("CƏHİZ", "Qızın ərə gedəndə ata evindən apardığı əşyalar"),
("NİŞANLI", "Toydan əvvəl söz verilmiş gələcək həyat yoldaşı"),
("ÇAĞLAYAN", "Səs-küylə axan dağ suyu, şəlalə"),
("İRİŞQA", "Qızılgülün yarpaqlarından çəkilən ətirli su yox, qatı yağ"),
("GÜLAB", "Qızılgüldən çəkilən ətirli su"),
("KÜPƏ", "Ağzı dar, qarnı enli saxsı qab"),
("SƏHƏNG", "Bulaqdan su daşımaq üçün saxsı qab"),
("QAZMAÇI", "Torpağı qazıb quyu açan usta"),
("SƏRRAF", "Köhnə vaxtlarda pul dəyişən adam"),
("ƏTTAR", "Ətir və ədviyyə satan tacir"),
("QALAYÇI", "Mis qabları ağardan usta"),
]
WORDS = [w for w in WORDS if w[0] not in ('İRİŞQA', 'QAZMAÇI')]
used = set()
for p in glob.glob('../app/src/main/assets/packs/*.json'):
    for L in json.load(open(p))['levels']:
        used |= {e['answer'] for e in L['entries']}
        if L.get('keyword'): used.add(L['keyword']['word'])
clash = [w for w, _ in WORDS if w in used]
dup = {w for w, _ in WORDS if [x for x, _ in WORDS].count(w) > 1}
if clash or dup: sys.exit(f'clash with packs: {clash}, duplicates: {dup}')
json.dump([dict(word=w, clue=c, source='claude-draft') for w, c in WORDS],
          open('../app/src/main/assets/word_of_day.json', 'w'), ensure_ascii=False, indent=1)
print(len(WORDS), 'words')
