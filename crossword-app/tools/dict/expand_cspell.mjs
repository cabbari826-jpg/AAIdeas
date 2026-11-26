// Expand the cspell trie (@cspell/dict-az-az) into a plain word list.
// Usage: node expand_cspell.mjs az-az.trie.gz out.txt  (needs: npm i cspell-trie-lib)
import {readFileSync,writeFileSync} from 'fs';import {gunzipSync} from 'zlib';
import * as lib from 'cspell-trie-lib';
const txt=gunzipSync(readFileSync(process.argv[2])).toString('utf8');
const trie=lib.importTrie(txt.split('\n'));
const words=[...lib.iteratorTrieWords(trie)];
writeFileSync(process.argv[3],words.join('\n'));console.log(words.length);
