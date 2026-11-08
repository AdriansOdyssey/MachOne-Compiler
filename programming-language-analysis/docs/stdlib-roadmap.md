# Contratos e plano da biblioteca padrão

Este inventário usa os 19 documentos e a EBNF fornecidos. Ele identifica APIs que já pertencem à linguagem e propõe apenas uma organização interna para implementá-las. Não cria palavras-chave, sintaxe de importação ou comportamento novo.

## Contratos definidos na especificação

| Área | API e operações documentadas | Fonte | Fronteira |
|---|---|---|---|
| I/O | `show`, `read` | 01, 09, 18 | Envios de mensagens comuns, com checagem no compilador e I/O no runtime. |
| Valores e tipos | `type`, `isa`, `&`, `::`, `==`, `!=`, `like`/`~` | 02, 03, 14, 19 | Semântica e tipos no compilador; valores e conversões no runtime. |
| Falhas | `(Fail) raise`, `kind`, `fail?`, `ok?`, `rescue` | 09, 15, 19 | Fail como valor e checagem estática de `try[T]`; recuperação executada somente em falha. |
| Ausência | `maybe[T]`, `nil`, `?:`, acesso seguro `'s`, `maybe_get` | 06, 09, 19 | Ausência distinta de Fail; Elvis executa o fallback somente para nil. |
| Strings | Strings comuns, raw, regex e heredoc; `char`; interpolação, formatação `:.N`, `upcase`, `stringify`, concatenação e repetição documentada | 02, 10, 15, 19 | Literais e interpolação no frontend; operações e protocolo de representação no runtime/métodos. |
| Números | Operadores aritméticos, comparação, bitwise, `even?`, `abs`, `times`, `upto`, `downto` | 03, 07, 18, 19 | Operadores e tipos são contratos da linguagem; iteração derivada usa lambdas. |
| Ranges | `to`, `..`, `by` | 03, 05, 07, 19 | Limites e passo possuem semântica definida; não são nomes de funções de uma biblioteca externa. |
| Vetores | `Vector new`, `element_at`, `first`, `last`, `length`, `'nth`, `'head`, `'tail`, `append`, `unshift`, `push`, `insert ... at`, `merge`, `pop`, `shift`, `delete`, `delete_nth`, `has?`, `has_all?`, `has_any?`, `reverse` | 05, 07, 18 | Armazenamento persistente RRB e operações básicas no runtime; transformações podem ser métodos MachOne. |
| Objetos | `get`, `get_element`, `maybe_get`, `element_at`, `copy`, `copy_with`, `has_key?`, `has_value?`, `has?`, operadores de coleção e acesso por tags | 06, 09, 19 | CHAMP com ordem de inserção persistente; chaves sempre tags. |
| Funcional | `result`, `each`, `collect`, `select`, `reject`; currying e algoritmos | 02, 07, 08, 10, 18 | Semântica de lambdas/captura no compilador; operações derivadas na biblioteca quando as primitivas estiverem corretas. |
| Records | `new`, `initialize`, `create`, `eq`/`op ==`, `stringify`, `slot`, `maybe_slot`, `&attrs` e derivações | 11, 12, 13, 15, 19 | Checagem, metadados e geração de métodos no compilador; armazenamento e envio no runtime. |
| Mensagens | `perform ... with`, `responds_to?`, `forward`, `intercept`, `proceed`, `proceed_with`, `redirect`, `target` | 15, 19 | O protocolo define envio e despacho, e não uma adaptação a interfaces Java. |
| Concorrência | IOBox `new`, inbox `copy`/`send`/`close`, outbox `receive`/`each`, tasks `result`, Time `after` e `seconds` | 16, 17, 19 | Runtime de comunicação e tempo; ownership e efeitos assíncronos exigem análise estática. |
| Padrões | Atores com métodos `tell`/`ask`; algoritmos de 18 | 17, 18 | Biblioteca escrita sobre recursos existentes; esses seletores não ganham status de palavra-chave. |

“Fonte” referencia `specification/MachOne/NN_*.mach`. Um item constar na tabela é evidência de contrato normativo, não declaração de implementação concluída.

## Regras que a biblioteca deve preservar

* `maybe[T]` representa T ou nil. `try[T]` representa T ou Fail. Não haverá um único wrapper que misture ausência e falha.
* `?:` não captura Fail. `rescue` não captura nil e não deve executar o handler no caminho de sucesso.
* Operações que podem falhar, incluindo acesso estrito de objetos, conversão e índice fora do limite, preservam os tipos de falha descritos em 09. Código derivado precisa tratar ou devolver o Fail; não converter exceções Java em sucesso.
* Acesso seguro de objetos e propriedades de records têm contratos próprios. A biblioteca não enfraquece privacidade, tipos dos atributos ou posse de outbox.
* Vetores usam a estrutura RRB e objetos usam CHAMP com ordem estável. Uma implementação temporária com listas/mapas comuns não satisfaz o contrato de estrutura/complexidade.
* A implementação de `show`, interpolação e coleções respeita `stringify` e sua precedência sobre Repr gerado.
* Métodos de biblioteca participam do mesmo multiple dispatch que os métodos de aplicação. As chamadas `some: 5 com: 2`, `some 5 com 2` e as formas mistas denotam o mesmo seletor quando sua aridade está resolvida.
* O parser e o analisador continuam responsáveis por decisões de sintaxe e fluxo. Mover `try`, `rescue` ou expansão de abstracts para uma biblioteca dinâmica não satisfaz o contrato estático.

## Ordem de implementação proposta

1. Consolidar os protocolos de erros/ausência, representação e mensagens com testes de tipos e execução. Esses protocolos são usados por toda a biblioteca.
2. Completar as consultas estritas/seguras e coleções persistentes, incluindo casos vazios, índices negativos e fora do limite, ordem estável e tipos parametrizados.
3. Implementar as operações de transformação (`each`, `collect`, `select`, `reject`) e os algoritmos documentados em MachOne, mantendo as primitivas de armazenamento no runtime.
4. Extrair da implementação Java monolítica os serviços internos de valores, mensagens, coleções, erros e I/O. Os nomes desses componentes internos não definem namespaces da linguagem.
5. Implementar os serviços concorrentes sobre as primitivas de 16, depois escrever e testar os padrões de 17 como biblioteca.
6. Definir explicitamente carregamento, versionamento e distribuição de bibliotecas quando houver um conjunto real de fontes MachOne para distribuir. A EBNF atual não define `import`/pacotes.

As primeiras bibliotecas devem nascer dos contratos existentes. Serviços de arquivos, rede, JSON e datas de calendário podem ser discutidos futuramente, mas ainda não possuem APIs normativas neste repositório e não foram introduzidos implicitamente.

## Verificação de cada API

Cada item implementado precisa ter testes de programa válido, erro de tipo, falha em execução quando prevista e integração com as demais operações. Alguns exemplos relevantes:

* `get #missing` produz Fail; `maybe_get #missing` produz nil; `?:` e `rescue` escolhem apenas seu caso correspondente.
* Um handler `rescue` que devolve nil mantém a distinção entre o tipo do resultado recuperado e o caminho de sucesso, em vez de fingir um valor T.
* Uma função que devolve `try[int]` pode retornar Fail em um guard e int no caminho normal. Usar o resultado como int exige recuperação ou refinamento de fluxo.
* `collect` conserva tipo do elemento resultante; `select`/`reject` conservam o tipo da coleção original; os predicados exigem bool.
* Um mesmo abstract derivado em records com campos diferentes deve ser especializado e checado em cada destino.
* `tell` e `ask` permanecem métodos comuns. As garantias vêm de behaviors, ownership e IOBoxes, não de uma exceção ao modelo de tipos.

## Questões de contrato ainda abertas

Há divergências normativas já registradas em `semantic-questions.md`: conversões totais versus `try[T]`, argumentos posicionais em vetor versus separados, custo de concatenação, captura em objetos persistentes e detalhes de effects/behaviors. Este plano não as resolve silenciosamente.

O nome `Math` em 11 é um record de exemplo, e `Money`, `Payment`, `Shipping`, `Temperature` e outras entidades de 19 são domínios de demonstração. Eles não serão inseridos automaticamente como tipos globais obrigatórios da biblioteca.


## Base executável na versão 0.2.0-dev

Já há testes executados para show/read como mensagens, tipos/representação, consultas estritas e seguras, each/collect/select/reject/times, lambdas/blocos e metadados de records. O projeto ainda não carrega uma biblioteca MachOne separada: essas operações estão no runtime e participam dos contratos do compilador. RRB/CHAMP, as demais APIs e concorrência continuam pendentes.
