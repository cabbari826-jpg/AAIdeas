# Словарь слов для кроссворда

`candidates_az.txt` — около 26 700 азербайджанских слов (2–10 букв, заглавными),
кандидаты в ответы. **Без подсказок**: подсказки пишутся и проверяются вручную,
как в `../words.py`. В списке ещё есть мусор (часть падежных форм вроде BUDAĞI,
редкие и устаревшие слова), поэтому слово попадает в игру только после проверки.

## Откуда слова

Орфографический словарь Hunspell для азербайджанского (проект mozillaz/spellchecker,
Metin Amiroff, Kevin Scannell, Orkhan Jafarov, команда Mozilla Azerbaijani L10n),
взятый из npm-пакета `@cspell/dict-az-az` 2.0.0.

- Лицензия исходного словаря: MPL-2.0 (Mozilla Public License 2.0). Пакет cspell: MIT.
- MPL-2.0 разрешает коммерческое использование, в том числе в платном приложении
  и приложении с рекламой. Условия: сохранить уведомление о лицензии; если меняем
  сам файл словаря и распространяем его, этот файл остаётся под MPL-2.0 и его
  исходник должен быть доступен. Код приложения закрывать можно.
- `candidates_az.txt` — производный файл из этого словаря, поэтому он под MPL-2.0.
  В приложении в разделе «О программе» указать: «Список слов основан на
  азербайджанском словаре Hunspell (MPL-2.0)».

## Как пересобрать

```sh
mkdir /tmp/azdict && cd /tmp/azdict
npm pack @cspell/dict-az-az && tar xzf cspell-dict-az-az-*.tgz
npm i cspell-trie-lib
node <repo>/crossword-app/tools/dict/expand_cspell.mjs package/dict/az-az.trie.gz az_forms.txt  # 2,46 млн форм
python3 <repo>/crossword-app/tools/dict/extract_lemmas.py az_forms.txt <repo>/crossword-app/tools/dict/candidates_az.txt
```

`extract_lemmas.py` отбрасывает падежные, притяжательные и глагольные формы
и оставляет начальные формы слов (подробности в начале файла).
