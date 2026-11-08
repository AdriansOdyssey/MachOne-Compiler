# Arquitetura MachOne

Os documentos originais de `specification/MachOne` e a referência recebida em `specification/stdlib/MachOne_stdlib.md` são preservados. Texto de exemplos, comentários, strings e heredocs é dado do programa, não instrução de instalação.

## Pipeline

`StandardPrelude` carrega `stdlib/portable.mach` do classpath e o analisa junto ao programa, mantendo origens separadas. A coleta de seletores é compartilhada; uma declaração de tipo do programa pode substituir uma definição portátil homônima.

ANTLR → AST com origem → contexto de composição → expansão pura → `ObjectModel` → resolução e análise estática → `MachIR` tipada → ASM → runtime JVM.

O frontend não depende de ASM. O backend emite bytecode Java 21 diretamente, sem fonte Java intermediária. O runtime não interpreta AST. A JVM fornece o garbage collector.

## Frontend e chamadas

`MessageSignatures` coleta seletores e aridades antes do parse. Isso permite `:` opcional nos envios, métodos declarados depois do uso, partes nomeadas, argumentos posicionais agrupados e varargs. As definições mantêm `:`. `show`, `read`, `each`, `then`, `otherwise`, `upto` e `downto` são mensagens comuns; os contratos e a execução da biblioteca resolvem esses seletores.

Comprehensions são nós próprios da AST, com bindings de iteração, expressão produzida, chave dinâmica opcional e filtro. O backend emite laços JVM; `IterationSupport` auxilia a criação das coleções e a ligação dos valores. Cada iteração tem frame próprio, preservando capturas de closures. Varargs são vetores no corpo do método; a seleção considera o prefixo fixo e o tipo dos elementos variádicos.

`LiteralDecoder` valida formatos sem executar o texto. Caracteres e operações de texto usam code points Unicode; bytes UTF-8 são obtidos pela mensagem `bytes`. A primeira coleta de aridades continua sendo uma aproximação contextual: não equivale a um parser semântico geral para qualquer combinação ambígua de sobrecargas.

## Tipos, composição e expansão

`TypeSystem` representa uniões, `maybe`, `try`, tipos funcionais, parâmetros e subtipagem. O modelo inclui a hierarquia numérica e os protocolos da biblioteca, sem transformar um record homônimo definido pelo usuário em um tipo nativo.

`ObjectModel` valida declarações, aliases, ciclos e conflitos. A composição `use T as nome` incorpora os campos e as mensagens do componente no record final. Grupos `(nome) has ...` são conferidos e expandidos em compile time; não acrescentam um objeto componente oculto. Defaults de Abstract Dispatches e bounded impls tornam-se ASTs independentes para os tipos concretos. Implementações explícitas têm precedência sobre defaults equivalentes. Bounds numéricos também se concretizam nos dez tipos numéricos nativos; bounds sobre coleções genéricas e nil ainda possuem lacunas.

`CompileTimeExpander` recebe os campos já compostos como contexto de `&attrs`. Avalia operações puras sobre constantes e metadados, expande `@for`, `@if` e splices e devolve AST comum. Código gerado passa pela resolução e tipagem usuais; não há concatenação de Java nem IO de programa durante essa fase. Limites de passos e profundidade diagnosticam expansões sem término.

`Analyzer` executa múltiplas passagens, substitui parâmetros, confere argumentos/retornos e refina tipos após testes de falha e ausência. `InitializationChecker` cobre caminhos diretos de construtores. MachIR mantém os tipos e resoluções por identidade; é uma IR estruturada, ainda sem CFG/SSA geral ou análise interprocedural completa.

## Mensagens e runtime

Métodos compilados são registrados como `MethodHandle`. A dominância compara receptor e todos os argumentos; candidatos incomparáveis são diagnosticados. Guards equivalentes seguem a ordem de declaração. O despacho múltiplo não depende do single dispatch da JVM.

O caminho externo de mensagem aplica `intercept`, procura uma implementação e recorre a `forward` ou ao alvo de `Proxy`. `message` carrega receptor, partes, argumentos e bloco; `proceed`, `proceed_with` e `redirect` retomam ou redirecionam o envio. O runtime distingue `#not_understood` de `#no_applicable`, evita ciclos de forwarding e preserva o remetente para verificar acessos privados. Envios de `self` não são interceptados novamente. `perform` é dinâmico e tem contrato estático `try[any]`; chamadas conhecidas continuam sendo verificadas antes do backend.

`DerivedSupport` implementa `Vec`, `Ord`, `Iter` e `Block`. Pelas decisões explícitas do usuário, `Ord` compara campos lexicograficamente, `Iter` percorre campos na ordem declarada e `Block` executa o único campo lambda. `Proxy` exige alvo identificável, com `target` como hook de substituição. As regras de composição de derivações nativas dos componentes ainda não constituem uma herança geral automática.

Frames guardam bindings e células mutáveis. Closures capturam snapshots dos bindings e compartilham células mutáveis. `NonLocalControl` conserva o destino lexical de `return` em `do`; lambdas com barras abrem sua própria fronteira. `&block` pertence ao frame do método e pode ser capturado por lambdas internas.

Fail é valor. O contexto acompanha falhas por identidade até consumo, recuperação ou término. Elvis trata nil; rescue trata Fail. Inteiros operam com wrap na largura do tipo, conforme decisão do usuário; literais e conversões `::` continuam verificando faixa. A representação de ulong preserva a precisão inteira.

## Biblioteca distribuída

A biblioteca tem duas partes executáveis:

* `stdlib/portable.mach`: `Pair`, `Queue` e `Stack`, compilados pelo mesmo pipeline do usuário.
* Primitivas nativas em `CoreLibrary`, `CollectionsLibrary`, `ExtraCollectionsLibrary`, `SystemLibrary` e `UtilitiesLibrary`, acompanhadas por contratos estáticos separados no compilador. Implementações MachOne aplicáveis são procuradas antes desses fallbacks.

Essas primitivas implementam operações de texto, números, funções, coleções, IO e formatos. `NativeMessages` fornece introspecção de seletores sem executar a operação. O JAR de um programa inclui os serviços do runtime e as definições portáteis compiladas; não precisa do compilador para executar.

O armazenamento de vetores, mapas e coleções extras utiliza estruturas auxiliares imutáveis da JVM. Metadados de vetores tipados preservam o tipo dos elementos, inclusive vazios, mas isso não implementa RRB, CHAMP, HAMT, árvores ou heaps persistentes normativos nem suas garantias de custo.

## Limites desta etapa

Concorrência, tasks, canais, ownership, behaviors e timers assíncronos foram adiados a pedido do usuário. `Duration`, datas e relógio monotônico são valores síncronos e não implementam esse modelo concorrente. Não foi inventada uma sintaxe de importação a partir da sugestão de arquivos da referência. APIs ausentes e propostas sem contrato suficiente constam em [stdlib-conformance.md](stdlib-conformance.md); o estado e a evidência de testes constam em [implementation-status.md](implementation-status.md) e [validation.md](validation.md).
