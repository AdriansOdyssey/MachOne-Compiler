# Estado real — MachOne 0.3.0-dev

O compilador gera bytecode JVM executável e permanece uma implementação parcial da linguagem completa. Esta etapa concentra orientação a objetos, mensagens, métodos, análise estática e biblioteca padrão. RRB/CHAMP e concorrência foram adiados explicitamente pelo usuário. A presença de um tipo ou de um seletor no código não constitui validação integral da especificação.

## Recursos implementados

| Área | Contratos presentes | Evidência específica |
|---|---|---|
| Pipeline e ferramentas | ANTLR, AST com origem, expansão, análise estática, MachIR, ASM, bytecode Java 21; CLI `check/run/build/test/help/--version`; JAR com runtime | `CompilerTest`, `CompileTimeExpansionTest`, validação de artefato JVM separado |
| Envios e métodos | `:` opcional em cada parte do envio; declarações com `:`; `show/read` ordinários; argumentos posicionais, varargs tipados, guards e despacho múltiplo | `MessageSyntaxTest`, `VarargsTest`, `ParserBoundaryTest` |
| Objetos e composição | Records parametrizados, inicialização customizada, campos privados, propriedades; `use T as nome` incorpora campos e mensagens; grupos `(nome) has ...` | `ObjectModelTest`, `ObjectAndBlockContractsTest`, `CompositionDerivationTest` |
| Derivações | `Init/Repr/Eq/Prop`, `Ord` lexicográfico, `Iter` por campos, `Vec`, `Block` com único campo lambda e `Proxy` | `CompositionDerivationTest`, `MessageProtocolTest` |
| Protocolos de mensagens | `perform`, `message`, `forward`, `intercept`, `proceed`, `proceed_with`, `redirect`, `target`; encaminhamento e subtipagem de Proxy | `MessageProtocolTest` com chamadas diretas e bytecode gerado |
| Abstracts e labels | Abstract Dispatches concretizados como AST, abstract data, bounded impls, labels e `&attrs`; expansão `@if/@for` e splices novamente tipados | `CompileTimeExpansionTest`, `ObjectIntegrationTest`, `CompositionDerivationTest` |
| Tipos | Primitivos signed/unsigned 8/16/32/64, float/double, char Unicode, regex, tag, type; aliases, uniões, genéricos, lambdas, maybe/try | `PrimitiveTypesTest`, `TypeSystemTest`, `IntegerWrapTest` |
| Funções e controle | Closures, captura, return lexical de do e local de lambda, `&block`; comprehensions de vetores/objetos com filtro e bindings locais | `NonLocalBlocksTest`, `AnonymousBlocksTest`, `ComprehensionTest` |
| Ausência e erros | Elvis para nil; rescue para Fail; refinamento após `ok?/fail?`; conversões verificadas e rastreamento de falhas por identidade | `ErrorSemanticsTest`, `LanguageIntegrationTest`, testes da biblioteca |
| Stdlib portátil | `Pair[A,B]`, `Queue[T]` e `Stack[T]` carregados e compilados como MachOne | `stdlib/portable.mach`, `LibraryIntegrationTest` |
| Stdlib nativa | Contratos estáticos e execução para números/texto/regex, controle como mensagens, combinadores funcionais, coleções, IO, dados e tempo síncrono | Famílias detalhadas em `stdlib-conformance.md` e suites correspondentes |

A entrega foi executada com **443 testes aprovados nos JDKs 21 e 25**, sem falhas nem ignorados. A tabela relaciona código e testes destinados ao contrato. A execução final da versão 0.3 está registrada em [validation.md](validation.md); nenhuma contagem de uma versão anterior deve ser usada como prova de que esta versão passou.

## Mudanças de comportamento desta etapa

* `then`, `otherwise`, `each`, `times`, `upto` e `downto` participam do envio de mensagens e dos contratos da biblioteca. As implementações do programa podem fornecer métodos aplicáveis.
* `perform` aceita uma tag ou partes em `vec[tag]`. Mensagens desconhecidas e tipos inaplicáveis têm falhas distintas. Interceptação e forwarding preservam bloco e remetente; o acesso dinâmico não concede permissão para ler campos privados.
* A composição não mantém um objeto componente chamado pelo alias: campos e métodos são incorporados ao record final, conforme decisão explícita do usuário. Colisões, ciclos e grupos de construção inválidos são diagnosticados.
* Comprehensions avaliam o filtro antes do valor produzido e conservam escopos por iteração. Objetos permitem bindings de chave e valor e chaves dinâmicas na produção.
* A divisão `/` exige `try[double]` quando não há prova de divisor diferente de zero, conforme decisão explícita do usuário; `/`, `//` e `%` classificam zero como `#zero_division`.
* A biblioteca é distribuída com o compilador. Não é preciso copiar `portable.mach` para cada programa. Seus records e métodos passam pela análise semântica e pelo backend normais.

## Limites conhecidos

* **Linguagem integral:** ainda falta catalogar e executar individualmente todos os exemplos positivos e negativos dos 19 documentos. A suíte atual não prova essa conformidade integral.
* **Parser e inferência:** coleta contextual de aridades não resolve todas as ambiguidades dependentes do tipo do receptor; inferência não é um solver geral de restrições. Layout e algumas formas normativas ainda têm lacunas.
* **Análise de fluxo:** não há CFG completo, prova interprocedural de inicialização nem análise geral de efeitos. Algumas construções válidas podem ser recusadas conservadoramente.
* **Composição:** importa campos, propriedades e métodos explícitos/defaults aplicáveis. A herança automática das derivações nativas de cada componente não está definida como uma cópia geral dos comportamentos gerados. Um record final deve solicitar suas próprias derivações quando necessário.
* **Bounds:** defaults são concretizados em records e nos tipos primitivos cobertos. Nil e todas as combinações de coleções genéricas não têm suporte completo a essa concretização.
* **Coleções:** APIs funcionam sobre armazenamento auxiliar da JVM. RRB, CHAMP, HAMT, árvores e heaps persistentes e suas complexidades não foram implementados nesta etapa. O compartilhamento/mutação contextual dos exemplos OMOP continua separado da mera existência de operações de mapa.
* **Stdlib:** não está completa. `stdlib-conformance.md` distingue operações implementadas, APIs ainda ausentes e propostas cujo contrato precisa ser definido. Não há interpretador de doctests, gerador de referência `@doc`, sistema de módulos/imports ou formato de publicação de pacotes.
* **Concorrência:** tasks, IOBoxes, canais, ownership, `alternate`, cancelamento, deadlock e behaviors seguem sem runtime. `tell/ask` de atores não se tornam palavras-chave.
* **Otimização:** MachIR ainda é estruturada; não há pipeline geral CFG/SSA/otimizações.

O próximo trabalho concreto é fechar os casos de conformidade restantes de mensagens e contratos da biblioteca, executar os exemplos normativos como fragmentos corretamente classificados e resolver as questões registradas em [semantic-questions.md](semantic-questions.md). As estruturas persistentes e a concorrência continuam em fases posteriores.
