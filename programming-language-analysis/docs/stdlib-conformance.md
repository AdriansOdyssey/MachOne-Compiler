# Conformidade da biblioteca padrão — 0.3.0-dev

Referência preservada: [MachOne_stdlib.md](../specification/stdlib/MachOne_stdlib.md). A marca `[S]` identifica contratos dos documentos originais; `[P]` identifica as propostas da referência. O pedido do usuário inclui o trabalho não concorrente, mas não preenche automaticamente assinaturas ou semânticas ausentes.

Este checklist cobre todas as famílias não concorrentes da referência. As linhas agrupadas não afirmam que cada variante recebeu todos os testes positivos, negativos, de tipos, execução e integração. **Implementado, sem validação completa** significa que há código e contrato estático; a evidência efetivamente executada está em [validation.md](validation.md). **Em desenvolvimento** identifica lacunas ou divergências dentro da família.

Os componentes são caminhos relativos a `compiler/src/main/java/mach/semantic` e `runtime/src/main/java/mach/runtime`, salvo indicação. As suítes ficam em `tests/src/test/java/mach`.

## Tipos, prelude e mensagens — §§1–5

| Referência | Recursos / seletores | Componente | Testes focais | Estado | Limites |
|---|---|---|---|---|---|
| 1 | Primitivos, type[T], vec[T], obj[T], lambda, maybe[T], try[T], union | TypeSystem/Analyzer | PrimitiveTypesTest, TypeSystemTest | Implementado, sem validação completa |  |
| 1 | Number, Integer, Signed, Unsigned, Real; Collection, Sequence, Mapping | TypeSystem/RuntimeSupport | CompositionDerivationTest | Implementado, sem validação completa |  |
| 1 | Comparable, Iterable, Enumerable; membership de Ord/Iter | TypeSystem/RuntimeSupport | CompositionDerivationTest | Implementado, sem validação completa |  |
| 1,9.5 | Range separado como tipo preguiçoso | Frontend/runtime | — | Não iniciado | Ranges atuais produzem vetores; a API desses vetores é utilizável. |
| 2 | show, read, print, eprint, ask, assert:msg | CoreLibrary/LibraryContracts | MessageSyntaxTest, StandardLibraryTest, CoreLibraryTest | Implementado, sem validação completa |  |
| 2,15.3 | exit e Process.exit | Prelude/SystemLibrary | — | Não iniciado |  |
| 2,7.2 | loop; being … while/until | Frontend/controle | — | Não iniciado |  |
| 3 | type, isa, &, ==, !=, eq, like, ~, stringify | Analyzer/RuntimeSupport | CompilerTest, MessageProtocolTest | Implementado, sem validação completa |  |
| 3 | inspect, copy, hash, nil?, present?, frozen?, tap, then_do | CoreLibrary/LibraryContracts | CoreLibraryTest | Em desenvolvimento | Revisar garantias de hash e frozen? para records mutáveis; não implica hash persistente normativo. |
| 3 | responds_to?, perform/perform:with | Analyzer/RuntimeSupport/NativeMessages | MessageProtocolTest | Implementado, sem validação completa |  |
| 3 | slot, maybe_slot, slots; privacidade em acesso dinâmico | Analyzer/RuntimeSupport | MessageProtocolTest, ObjectAndBlockContractsTest | Implementado, sem validação completa |  |
| 3 | initialize, forward, intercept, target, Proxy | ObjectModel/Analyzer/RuntimeSupport | MessageProtocolTest, CompositionDerivationTest | Implementado, sem validação completa |  |
| 3 | message: receiver, name, parts, args, block; proceed, proceed_with, redirect | Analyzer/RuntimeSupport | MessageProtocolTest | Implementado, sem validação completa |  |
| 4 | new, create; falha de initialize | ObjectModel/Analyzer/RuntimeSupport | ObjectAndBlockContractsTest | Implementado, sem validação completa |  |
| 4 | name, supertypes, subtypes, attrs, methods, derives? | Analyzer/RuntimeSupport | MessageProtocolTest, ModulesLibraryTest | Em desenvolvimento | Introspecção de seletores nativos e hierarquias ainda precisa de cobertura exaustiva. |
| 4 | &attrs, &block, &it, self; labels no contexto composto | CompileTimeExpander/Analyzer | CompileTimeExpansionTest, CompositionDerivationTest | Implementado, sem validação completa |  |
| 5 | and/or/not/&&/\|\|, Elvis ?:; xor, bool.to_int | Analyzer/JvmBackend/CoreLibrary | CompilerTest, CoreLibraryTest | Implementado, sem validação completa |  |

## Números e controle como mensagens — §§6–7

| Referência | Recursos / seletores | Componente | Testes focais | Estado | Limites |
|---|---|---|---|---|---|
| 6.1 | + - * / // % **; comparações e bitwise | Analyzer/RuntimeSupport | CompilerTest, PrimitiveTypesTest, IntegerWrapTest | Em desenvolvimento | Wrap inteiro aprovado; efeitos de falha e todos os caminhos dinâmicos exigem cobertura por operador. |
| 6.1 | ::T; ranges inclusivos/exclusivos com by | Analyzer/RuntimeSupport | CompilerTest, PrimitiveTypesTest | Implementado, sem validação completa |  |
| 6.2 | abs, even?, odd?, zero?, positive?, negative?, sign, succ, pred | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.2 | min, max, clamp, between?, divmod, div, mod, rem, pow, pow:mod | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.2 | sqrt, cbrt, gcd, lcm, factorial, prime?, digits, bit_length | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.2 | to_double, to_float, to_int, to_long, to_char, to_str, to_str:base | CoreLibrary/LibraryContracts | CoreLibraryTest | Em desenvolvimento | to_x nativo e ::T têm contratos distintos; ::T mantém verificação de faixa. |
| 6.2 | format, round, floor, ceil, trunc, round:places; nan?/infinite?/finite? | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.2 | (T) max_value/min_value | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.3 | times, upto, downto, step:every | CoreLibrary/LibraryContracts | CoreLibraryTest, UtilityLibraryTest | Implementado, sem validação completa |  |
| 6.3,16 | seconds, minutes, hours, days, millis | UtilitiesLibrary/UtilityContracts | UtilityLibraryTest | Implementado, sem validação completa |  |
| 6.4 | Math.pi/e/tau/infinity/nan | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.4 | Math.sin/cos/tan/asin/acos/atan/atan2:x/sinh/cosh/tanh/exp/ln/log:base/log2/log10 | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.4 | Math.sqrt/hypot:b/floor/ceil/round/abs/min/max/to_radians/to_degrees | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.4 | Math.sum/mean/median/variance/stddev | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 6.5 | Random.int/double/bool/pick/shuffle/seed/bytes/uuid | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 7.1 | then, otherwise, then:otherwise; and_then, or_else em bool | Frontend/Analyzer/CoreLibrary | MessageSyntaxTest, CoreLibraryTest | Implementado, sem validação completa |  |
| 7.2 | if/elsif/else, unless, when/is/default, while/until, for, break/next/return | Frontend/Analyzer/JvmBackend | CompilerTest, NonLocalBlocksTest | Implementado, sem validação completa |  |
| 7.2,10.3.1 | Comprehensions: produção, filtro, chave dinâmica, dois bindings, captura por iteração | Frontend/Analyzer/JvmBackend | ComprehensionTest | Implementado, sem validação completa |  |

## Texto, chars e regex — §8

| Referência | Recursos / seletores | Componente | Testes focais | Estado | Limites |
|---|---|---|---|---|---|
| 8.1 | Interpolação/formatação; raw, heredoc, regex e char literais | LiteralDecoder/Frontend | LiteralDecoderTest, PrimitiveTypesTest | Implementado, sem validação completa |  |
| 8.2 | Concatenação, repetição, comparação, like, << e remoção \\ | Analyzer/CoreLibrary | CoreLibraryTest, QuotedCollectionSyntaxTest | Implementado, sem validação completa |  |
| 8.2 | nth índice/range/negativo, head, tail com apóstrofo | Frontend/LibraryContracts/CoreLibrary | QuotedCollectionSyntaxTest | Implementado, sem validação completa |  |
| 8.3 | upcase/downcase/capitalize/swapcase/title_case | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.3 | trim/trim_left/trim_right/squeeze/center/pad_left:with/pad_right:with | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.3 | reverse/repeat/length/size/empty?/blank? | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.3 | has?/starts_with?/ends_with?/index_of/last_index_of/count | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.3 | match?/match/scan; digit?/alpha?/alnum?/space?/upper?/lower? | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.3 | replace:with/replace_first:with/delete/slice:to/take/drop | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.3 | split/split_lines/lines/words/chars/bytes/to_tag/to_sym/escape/unescape | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.3 | str.format:values; str.encode/decode | CoreLibrary/LibraryContracts | — | Não iniciado | Interpolação, bytes e módulos Base64/Hex existem; isso não implementa estes seletores de str. |
| 8.3 | compare/casecmp/hash | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.4 | char.code/upcase/downcase/digit?/alpha?/space?/to_str/to_int/succ/pred | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 8.5 | regex.match/match?/scan/replace:with/split/source/flags; grupos nomeados | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |

## Vetores, ranges e objetos — §§9–10

| Referência | Recursos / seletores | Componente | Testes focais | Estado | Limites |
|---|---|---|---|---|---|
| 9.1,10.1 | [a,b], %[a b], {#k:v}, #{k:v}, %{k:v}; Vector.new e vec[T].new | Frontend/CollectionContracts/CollectionsLibrary | QuotedCollectionSyntaxTest, CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.2 | element_at, first/last, first:n/last:n, length; nth/head/tail | CollectionContracts/CollectionsLibrary | StandardLibraryTest, QuotedCollectionSyntaxTest | Implementado, sem validação completa |  |
| 9.2 | has?/has_all?/has_any?/empty?/any?/none?/index_of/last_index_of/count/sample | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.3 | append/unshift/push/insert:at/merge/concat e +/<< />> | Analyzer/CollectionContracts/CollectionsLibrary | CollectionsLibraryTest, QuotedCollectionSyntaxTest | Implementado, sem validação completa |  |
| 9.3 | pop/shift/delete/delete_nth/copy/put:at/compact | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.3 | vec.copy_with {#i:x} | CollectionContracts/CollectionsLibrary | — | Bloqueado por ambiguidade | obj exige chaves tag; a notação de índices #i e a forma de atualização precisam ser fixadas. put:at está disponível. |
| 9.4 | each/each_with_index/collect/map/select/filter/reject | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest, AnonymousBlocksTest | Implementado, sem validação completa |  |
| 9.4 | reduce:with/fold:with/inject:with; sum/product/sum_by/average | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.4 | min/max/min_by/max_by/sort/sort_by/sort_with/reverse/uniq | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.4 | flatten/flat_map/zip/unzip/take/drop/take_while/drop_while/slice:to | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.4 | find/detect/find_index/all?/any?/none?/one? | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.4 | group_by/partition/tally/each_slice/each_cons/chunk_while | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.4 | rotate/shuffle/combinations/permutations/product:other/join | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 9.4 | to_obj/to_set/intersect/union/difference/binary_search | CollectionContracts/ExtraCollectionContracts/CollectionsLibrary/ExtraCollectionsLibrary | CollectionsLibraryTest, ExtraCollectionsTest | Implementado, sem validação completa |  |
| 9.4,12,18 | cycle, lazy, Iterator.next/take/collect | Biblioteca/geração preguiçosa | — | Bloqueado por ambiguidade | Faltam contrato de término/produção e semântica de armazenamento/efeitos; nenhum iterador infinito fictício é registrado. |
| 9.5 | Mensagens de vec em ranges materializados | Frontend/Analyzer/CollectionsLibrary | CompilerTest, CollectionsLibraryTest | Em desenvolvimento | Range preguiçoso separado e step:n de Range ainda não estão implementados. |
| 20 | Ranges de char ou limites não inteiros | Frontend/Analyzer/runtime | — | Em desenvolvimento | A forma normativa implementada usa inteiros; a tabela nova amplia os tipos sem definir todas as regras de passo. |
| 10.2 | get/maybe_get/get_element/element_at, acesso seguro por tag e caminho | Analyzer/CollectionContracts/RuntimeSupport | StandardLibraryTest, QuotedCollectionSyntaxTest | Em desenvolvimento | get_element preserva o valor de 06_omop.ma; a nova tabela descreve um objeto de uma entrada e diverge. |
| 10.2 | has_key?/has_value?/has? de par; keys/values/pairs/fetch:default/dig/key_of | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 10.3 | copy/copy_with/merge/deep_merge/update:with/delete_key; \| e << | Analyzer/CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 10.3 | Remoção por valores com delete/\\ e por chave com delete_key | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest, QuotedCollectionSyntaxTest | Em desenvolvimento | 06_omop remove valores; a nova referência descreve remoção por chave para o operador. Divergência preservada. |
| 10.3 | select/reject/collect/map_values/map_keys/filter_keys/except/slice/invert/to_vec | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 10.3 | obj.each/reduce/all?/any?/none?/one?/count/find/min_by/max_by/sort_by/group_by | CollectionContracts/CollectionsLibrary | CollectionsLibraryTest | Implementado, sem validação completa |  |
| 06 original | Bindings de slots e reparenting de closures ao copiar objetos OMOP | Semântica de captura/mapas | — | Bloqueado por ambiguidade | Os exemplos mutam slots por nomes capturados; não se presume que APIs de mapa resolvam essa semântica. |

## Erros, funções, records e composição — §§11–13

| Referência | Recursos / seletores | Componente | Testes focais | Estado | Limites |
|---|---|---|---|---|---|
| 11 | Fail.raise, fail?/ok?/kind; rescue e Elvis; tipos try/maybe | Analyzer/RuntimeSupport | ErrorSemanticsTest, TypeSystemTest | Implementado, sem validação completa |  |
| 11 | Fail.message/cause/trace/wrap | CoreLibrary/RuntimeSupport/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 11 | unwrap/unwrap_or/or_else/map/and_then/maybe_value/to_maybe/to_try | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 11 | Tags de mensagens e falhas de domínio/índice/IO/conversão | RuntimeSupport e bibliotecas | MessageProtocolTest, CoreLibraryTest, ModulesLibraryTest | Em desenvolvimento | O catálogo de tags deve ser confrontado individualmente com todas as operações; tags de concorrência estão adiadas. |
| 12 | result, &block, captura e fronteiras de return | Analyzer/JvmBackend/NonLocalControl | AnonymousBlocksTest, NonLocalBlocksTest | Implementado, sem validação completa |  |
| 12 | arity/curry/compose/then_do/partial/memoize/flip/Fn.identity/Fn.constant | CoreLibrary/LibraryContracts | CoreLibraryTest | Implementado, sem validação completa |  |
| 13.1 | Init/Repr/Eq/Prop | ObjectModel/Analyzer/RuntimeSupport | ObjectAndBlockContractsTest | Implementado, sem validação completa |  |
| 13.1 | Ord/Iter/Block/Vec/Proxy | ObjectModel/DerivedSupport/RuntimeSupport | CompositionDerivationTest, MessageProtocolTest | Implementado, sem validação completa |  |
| 13.1 | Derivações propostas Hash/Copy/Default/Json/Enum/Debug | Metaprogramação/biblioteca | — | Bloqueado por ambiguidade | Faltam contratos de geração, assinaturas e conflito; métodos hash/copy ordinários não equivalem à derivação. |
| 13.2 | readable/configurable/accessible/proxied e labels livres | ObjectModel/CompileTimeExpander | CompileTimeExpansionTest, CompositionDerivationTest | Implementado, sem validação completa |  |
| 13.3 | use T as nome; (nome) has; campos/mensagens e tipos parametrizados | ObjectModel/Frontend/Analyzer | CompositionDerivationTest | Implementado, sem validação completa |  |
| 10 original | Posicionais agrupados e varargs tipados | Frontend/Analyzer/JvmBackend | VarargsTest | Implementado, sem validação completa |  |
| 13–14 originais | Abstract Dispatches, abstract data e bounded impls em records/primitivos | ObjectModel/TypeSystem/Analyzer | ObjectIntegrationTest, CompositionDerivationTest | Em desenvolvimento | Concretização de bounds para nil e coleções genéricas não é geral. |

## IO, tempo, formatos e coleções extras — §§15–19

| Referência | Recursos / seletores | Componente | Testes focais | Estado | Limites |
|---|---|---|---|---|---|
| 15.1 | Console.clear/color:text/width | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 15.2 | File.read/read_lines/write:text/append:text/exists?/delete/copy:to/move:to/size/each_line/open:mode | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 15.2 | Path.join/base/dir/ext/absolute/exists?/file?/dir?; Dir.list/create/delete/glob/cwd/home/tmp | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 15.3 | Env.get/set/all; Process.args/pid/run:args/cwd; System.os/arch/cpus/hostname/version | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 15.4 | Http.get/post:body | Rede opcional | — | Não iniciado | Modelo do objeto de resposta e política de falhas ainda precisam de contrato de biblioteca. |
| 15.4 | Socket | Rede opcional | — | Bloqueado por ambiguidade | A referência só contém “Socket …”, sem operações ou assinaturas. |
| 16 | Duration unidades, + / - / *, as_seconds/as_millis/to_str | UtilitiesLibrary/UtilityContracts | UtilityLibraryTest | Implementado, sem validação completa |  |
| 16 | Time.now; Instant.elapsed/subtração/after síncrono | UtilitiesLibrary/UtilityContracts | UtilityLibraryTest | Implementado, sem validação completa |  |
| 16 | Date.today/new/year/month/day/weekday/add_days/add_months/format/parse/leap?/days_until/comparação | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 16 | DateTime.now/utc/zone/to_unix/format/parse/hour/minute/second | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 17 | Json.parse/generate/pretty; Csv.parse:header/generate | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 17 | Toml.parse/generate e Yaml.parse/generate | Formatos opcionais | — | Não iniciado | Não há parser parcial apresentado como implementação dos formatos. |
| 17 | Base64.encode/decode; Hex.encode/decode; Hash.sha256/md5/sha1; Uuid.v4/parse; Url.encode/decode/parse | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 17 | Text.wrap:width/indent/dedent/levenshtein; Number.parse/format_thousands; Bits.count/reverse/parity | UtilitiesLibrary/UtilityContracts | UtilityLibraryTest | Implementado, sem validação completa |  |
| 17 | Locale, Color.rgb/hex/hsl, Vec2/Vec3 | Biblioteca adicional | — | Bloqueado por ambiguidade | Representações, assinaturas e domínios completos não foram definidos; Vec2 de exemplo continua implementável em código do usuário. |
| 18 | Set.add/delete/has?/union/intersect/difference/subset?/length/to_vec | ExtraCollectionsLibrary/ExtraCollectionContracts | ExtraCollectionsTest | Implementado, sem validação completa |  |
| 18 | Dictionary.get/maybe_get/put/delete/has_key?/keys/values/pairs/merge | ExtraCollectionsLibrary/ExtraCollectionContracts | ExtraCollectionsTest | Implementado, sem validação completa |  |
| 18 | Deque.push_front/push_back/pop_front/pop_back/front/back | ExtraCollectionsLibrary/ExtraCollectionContracts | ExtraCollectionsTest | Implementado, sem validação completa |  |
| 18 | PriorityQueue.insert:with/pop_min/peek; SortedSet/SortedMap.first/last/range | ExtraCollectionsLibrary/ExtraCollectionContracts | ExtraCollectionsTest | Em desenvolvimento | APIs usam armazenamento auxiliar; domínio da ordenação e comportamentos vazios devem permanecer explícitos. |
| 18 | Bag.add/count | ExtraCollectionsLibrary/ExtraCollectionContracts | ExtraCollectionsTest | Implementado, sem validação completa |  |
| 18 | Pair.first/second/swap; Queue.enqueue/dequeue/peek; Stack.push/pop/peek | stdlib/portable.ma | LibraryIntegrationTest | Implementado, sem validação completa |  |
| 18 | Mesmas mensagens Enumerable em todas as coleções extras | ExtraCollectionsLibrary/ExtraCollectionContracts | ExtraCollectionsTest | Em desenvolvimento | Há delegação e callbacks tipados, mas não cobertura integral de cada combinação de receptor/seletor. |
| 18 | Tuple, Graph.add_edge/neighbors/shortest_path:to, Matrix.rows/cols/*/transpose/det, Ring | Biblioteca adicional | — | Bloqueado por ambiguidade | Faltam construtores, tipos/representações, assinaturas ou regras de erro; Ring não tem API listada. |
| 19 | Reflect.methods/attrs/supertypes/subtypes/responds_to/send | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 19 | Debug.trace/dump/assert:msg; Log.info/warn/error/debug/level | SystemLibrary/SystemContracts | ModulesLibraryTest | Implementado, sem validação completa |  |
| 19 | Debug.time | SystemLibrary/SystemContracts | — | Não iniciado |  |
| 19 | Test.suite/test/expect:to_be/expect_fail:kind/run; Bench.run:times | SystemLibrary/SystemContracts | ModulesLibraryTest | Em desenvolvimento | Utilitários básicos executáveis não equivalem à suíte JUnit de conformidade do compilador. |
| 19,23 | Doctest; referência e testes gerados de @doc | Documentação/ferramentas | — | Bloqueado por ambiguidade | Convenção de avaliação dos comentários e cobertura gerada ainda não implementadas. |
| 19 | Compile.type_of/error | Metaprogramação/ferramentas | — | Bloqueado por ambiguidade | A fase de avaliação e seus efeitos não foram definidos; o avaliador puro existente não inventa esses métodos. |

## Fora desta etapa por decisão explícita do usuário

* Toda a seção 14: tasks, envios `!/?`, spawn, inbox/outbox/IOBox, canais, alternate, ownership, fechamento concorrente, cancelamento, deadlock, timers, atores e behaviors. `sleep`, `Time.after/every/timeout`, `Log.to:outbox` e APIs dependentes também foram adiados.
* RRB-Vector, CHAMP, HAMT e implementações de árvores/heaps persistentes. As APIs imutáveis atuais não prometem as complexidades de armazenamento da referência.

## Questões transversais

* A seção 20 é um índice dos operadores, não uma implementação adicional. Uma operação infixa funcionando não prova que todos os seus caminhos de `perform`, interceptação e forwarding estejam conformes.
* A seção 21 sugere arquivos e camadas, mas não define um sistema completo de importação. A biblioteca portátil é carregada como recurso do compilador; não foi acrescentada uma palavra-chave de pacote por inferência.
* A seção 22 e as divergências com os exemplos originais continuam registradas em [semantic-questions.md](semantic-questions.md). A decisão anterior do usuário prioriza a sintaxe dos exemplos sobre a EBNF.
* A seção 23 descreve a geração desejada de referência e testes. Comentários `; ->` e blocos `@doc` não são contados como testes executados.
* Strings contam code points Unicode; `bytes` produz bytes UTF-8. Não há alegação de segmentação por grafemas.

Validação da entrega: a suíte completa de 443 testes passou nos JDKs 21 e 25, incluindo os casos das famílias listadas. Estados de validação incompleta permanecem onde a família abrange operações sem teste individual ou propostas não implementadas. Veja `validation.md` para a contagem por suíte.
