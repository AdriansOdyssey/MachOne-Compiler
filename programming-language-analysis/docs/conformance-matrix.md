# Matriz de conformidade — 0.3.0-dev

A biblioteca recebida possui checklist próprio em [stdlib-conformance.md](stdlib-conformance.md). A execução atual da suíte está em [validation.md](validation.md). Cada linha abaixo corresponde a uma seção normativa; exemplos positivos, negativos, tipos, execução e integração ainda precisam ser catalogados individualmente. Esta matriz não considera uma seção completa quando apenas um de seus exemplos funciona.

| Fonte | Recurso | Componente | Testes | Estado |
|---|---|---|---|---|
| `01_basic.mach` | Hello World | frontend/semantic/backend | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `01_basic.mach` | Comentários | frontend/semantic/backend | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `01_basic.mach` | Binding, Atribuição e Variáveis | frontend/semantic/backend | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `01_basic.mach` | Shadowing e Rebinding | frontend/semantic/backend | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `01_basic.mach` | Entrada e Saída (I/O) | frontend/semantic/backend | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `02_types.mach` | Tipos de Dados | semantic/types/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `02_types.mach` | Lambdas | semantic/types/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `02_types.mach` | Conversão de Tipos (::) | semantic/types/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `03_operators.mach` | Operadores | semantic/backend/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `04_conditions.mach` | Estruturas Condicionais | frontend/backend | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `05_vectors.mach` | Vetores e API de coleção | Frontend, Analyzer, CollectionsLibrary | CollectionsLibraryTest, QuotedCollectionSyntaxTest | Em desenvolvimento |
| `06_omop.mach` | Mapas de Objetos (Vetores de Pares) | Frontend, Analyzer, CollectionsLibrary | StandardLibraryTest, CollectionsLibraryTest | Em desenvolvimento |
| `06_omop.mach` | Programação Orientada à Mapas de Objetos (OMOP) | Frontend, Analyzer, RuntimeSupport | Contratos de captura/reparenting pendentes | Bloqueado por ambiguidade |
| `07_loops.mach` | Estruturas de Repetição | frontend/backend | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `08_functional.mach` | Recursos Funcionais | frontend/semantic/runtime | Suítes 0.2 (cobertura parcial) | Em desenvolvimento |
| `09_errors.mach` | Tipo Nulo (nil) | semantic/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `09_errors.mach` | Erros como tipos (Fail) | semantic/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `09_errors.mach` | Tratativa de Erros (rescue) | semantic/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `09_errors.mach` | Propagando Falhas | semantic/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `09_errors.mach` | Falhas em Coleções | semantic/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `10_methods.mach` | Métodos de Despacho | symbols/types/dispatch | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `10_methods.mach` | Argumentos Posicionais e Variádicos | symbols/types/dispatch | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `10_methods.mach` | Uniões e Tipos Genéricos | symbols/types/dispatch | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `10_methods.mach` | Implementando Tipos | symbols/types/dispatch | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `10_methods.mach` | Lambdas em Métodos | symbols/types/dispatch | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `11_records.mach` | Records | records/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `11_records.mach` | Encapsulamento Automático | records/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `11_records.mach` | Métodos "Estáticos" | records/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `11_records.mach` | Métodos Mágicos | records/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `11_records.mach` | Generics | records/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `12_compile_control.mach` | Geração de código em tempo de compilação | frontend/semantic/runtime | Suítes 0.2 (cobertura parcial) | Em desenvolvimento |
| `12_compile_control.mach` | O operador &attrs | frontend/semantic/runtime | Suítes 0.2 (cobertura parcial) | Em desenvolvimento |
| `13_abstract_methods.mach` | Composição Estrutural | abstracts/semantic | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `13_abstract_methods.mach` | Abstract Dispatches | abstracts/semantic | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `13_abstract_methods.mach` | Labels | abstracts/semantic | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `14_abstract_data.mach` | Polimorfismo | types/dispatch | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `14_abstract_data.mach` | Abstract Datas | types/dispatch | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `14_abstract_data.mach` | Diferença de Abstract Methods e Abstract Data | types/dispatch | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `15_message_programming.mach` | Os 4 Pilares da Programação Orientada à Mensagens | dispatch/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `15_message_programming.mach` | Pilar 1: Responder Mensagens e perform | dispatch/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `15_message_programming.mach` | Pilar 2: Redirecionar Mensagens (forward) | dispatch/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `15_message_programming.mach` | Pilar 3: Interceptar Mensagens (intercept) | dispatch/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `15_message_programming.mach` | Pilar 4: Proxyficar Mensagens (Proxy) | dispatch/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `15_message_programming.mach` | Métodos Mágicos: Operadores (on op) | dispatch/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `15_message_programming.mach` | Métodos Mágicos: initialize e new | dispatch/runtime | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `16_concurrence.mach` | Concorrência | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Métodos Assíncronos | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Blocos Assíncronos (Tasks) | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Anatomia de Canais: Inboxes e Outboxes | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Comunicação Básica | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Comunicação Múltiplos Produtores | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Buffers e Capacidade | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Fechamento e Ciclo de Vida | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Iteração Exclusiva em Outboxes | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Seleção com Receive (Multiplexação de Outboxes) | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Receive em Loops | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Timeouts Orientados a Outbox | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `16_concurrence.mach` | Canais em Métodos | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `17_behaviors.mach` | Actors e Behaviors | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `17_behaviors.mach` | Violações da Semântica Rejeitadas pelo Compilador | Ver mapa de componentes abaixo | Pendente | Não iniciado |
| `18_algorithms.mach` | Fibonacci Recursivo | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Máximo Divisor Comum (Euclides) | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Torres de Hanói | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Currying | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Quicksort | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Busca Binária | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Crivo de Eratóstenes | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Conjectura de Collatz | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Trabb Pardo-Knuth | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `18_algorithms.mach` | Dijkstra | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Tudo é objeto. Tudo se comunica. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Há mais de um caminho para o mesmo destino. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Mensagens acima de mecanismos. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Paradigmas são ferramentas, não dogmas. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Código que se revela como prosa. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | O que é complexo por dentro deve ser simples por fora. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Metaprogramar é fazer do domínio uma linguagem. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | De tipos nascem subtipos; de ambos, possibilidades. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | A linguagem deve se adaptar ao problema, não o contrário. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Concorrência é comunicação, não caos. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Abstrações devem revelar, não ocultar. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Consistência importa mais que familiaridade. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |
| `19_philosophy.mach` | Elegância nasce da liberdade com disciplina. | conformance | CompilerTest (cobertura parcial) | Em desenvolvimento |

## Componentes responsáveis

* Básico, operadores, condições e loops: frontend, semantic, MachIR, backend, RuntimeSupport.
* Tipos e métodos: semantic/types e dispatch; inferência, resolução e especialização.
* Vetores/objetos: stdlib e runtime (RRB/CHAMP ainda não iniciados).
* Funcional: frontend, análise de capturas e backend de closures.
* Erros: análise de fluxo try/maybe e valores Fail de runtime.
* Records: resolução de campos, acesso, instanciação parametrizada, metadados.
* Compilação controlada, abstracts e labels: expansão AST antes da tipagem final.
* Mensagens: seleção estática, dispatcher runtime, intercept/forward/proxy.
* Concorrência: análise afim de ownership, scheduler, IOBox, alternate.
* Behaviors: análise de efeitos e mensagens assíncronas; tell/ask na biblioteca.
* Algoritmos e filosofia: conformidade integrada com todas as camadas.

As suítes estão em `tests/src/test/java/mach`: CompilerTest, MessageSyntaxTest, ObjectModelTest, ObjectIntegrationTest, LanguageIntegrationTest, ErrorSemanticsTest, TypeSystemTest, CompileTimeExpansionTest, PrimitiveTypesTest, NonLocalBlocksTest, AnonymousBlocksTest, ObjectAndBlockContractsTest, IntegerWrapTest e StandardLibraryTest, além dos testes do frontend.

## Casos focais com evidência anterior (0.2)

“Validado por testes” abaixo se refere ao caso focal descrito, não à seção inteira.

| Caso focal | Teste JUnit | Estado |
|---|---|---|
| Hello World real | helloWorld | Validado por testes |
| let e final | rebindingUsesPreviousValue, finalCannotRebind | Validado por testes |
| Tipos e intervalos | strongTyping, byteRange, contextualIntegerType, contextualFloat | Validado por testes |
| Operadores/precedência | operatorPrecedence, bitwise, strongEqualityAndSimilarity | Validado por testes |
| Controle e escopos | conditionalAndScope, unlessAndElsif, loopsAndMutation, whileUntilBreakAndNext | Validado por testes |
| Métodos/sign/retornos | typedMessages, signAppliedToMethod, inferMethodReturn | Validado por testes |
| Despacho em todos os argumentos | multipleDispatchAllArguments, mostSpecificDispatch | Validado por testes |
| Candidatos incomparáveis | incomparableDispatchRejectedStatically | Validado por testes |
| Guards ordenados | guardsInOrder | Validado por testes |
| T repetido e retorno genérico | genericRepeatedTypeConstraint, genericRejectsConflictingTypes, genericReturnSubstitution | Validado por testes |
| Currying/captura/return de lambda | curryFromSpecification, closureCapturesOldImmutableBinding, closureSharesMutableCell, lambdaReturnIsLocal | Validado por testes |
| try/conversão/rescue/Fail ignorado | conversionAndRescue, tryCannotBeUsedAsPlainValue, failableDivision, ignoredFailureAborts | Validado por testes |
| Campos/privacidade/self | privateFieldInsideImplAndSelfMutation, privateFieldRejectedOutsideImpl, selfUpdateIsStaticallyChecked | Validado por testes |
| Labels/Prop/metadados | recordProperties, readableRejectsSetter, labelIntrospection | Validado por testes |
| Abstract simples | abstractCopiesCompiledMethods | Validado por testes |
| JAR sem classpath do compilador | executableJarWithoutCompilerClasspath | Validado por testes |
| Especificação intacta | originalSpecificationUnmodified | Validado por testes |
| @for/@if, splice, Abstract Dispatch expandido e tipado | CompileTimeExpansionTest (inclui Deposit da especificação) | Validado por testes |
| RRB e CHAMP normativos | Ainda não há testes | Não iniciado |
| Tasks/IOBox/alternate/deadlock/cancelamento | Ainda não há testes | Não iniciado |
| Ownership/captura de outbox/uso após move | Ainda não há testes | Não iniciado |
| Behaviors/tell/ask de biblioteca | Ainda não há testes | Não iniciado |
| Semântica dos conflitos catalogados | docs/semantic-questions.md | Bloqueado por ambiguidade |

A orientação do usuário resolveu a precedência externa: exemplos têm prioridade sobre a EBNF. Isso não resolve automaticamente inconsistências entre dois exemplos, que continuam registradas.


## Cobertura herdada — 0.2.0-dev

| Contrato | Suíte | Estado |
|---|---|---|
| Envios sem `:`, formas mistas, nomes e aridades | MessageSyntaxTest | Validado por testes |
| show/read são Calls e podem ser declarados | MessageSyntaxTest, ObjectAndBlockContractsTest | Validado por testes |
| Hierarquias e concretização de abstracts | ObjectModelTest, ObjectIntegrationTest | Validado por testes |
| maybe/try, rescue, refinamento e propagação | TypeSystemTest, ErrorSemanticsTest, LanguageIntegrationTest | Validado por testes |
| Inicializadores, Eq, stringify e acesso customizado | ObjectAndBlockContractsTest | Validado por testes |
| Return lexical de do e retorno local de lambda | NonLocalBlocksTest | Validado por testes |
| Blocos anônimos e &block, funções de ordem superior | AnonymousBlocksTest | Validado por testes |
| Primitivos, literais e tipos parametrizados | PrimitiveTypesTest, LiteralDecoderTest | Validado por testes |
| Wrap nas larguras inteiras, conversão verificada | IntegerWrapTest | Validado por testes |
| APIs iniciais de biblioteca e I/O em JAR | StandardLibraryTest | Validado por testes |
| Composição use, derivações e quatro pilares | Novos contratos na tabela 0.3 abaixo; conformidade integral pendente | Em desenvolvimento |

Uma seção inteira permanece Em desenvolvimento enquanto algum contrato normativo dela faltar, mesmo que seus casos focais já estejam validados. Os estados das tabelas de casos focais têm prioridade sobre rótulos agregados antigos por arquivo.


## Novos contratos — 0.3.0-dev

A existência da suíte na tabela indica o local da evidência, sem substituir a execução final nem afirmar que toda a seção normativa está completa.

| Contrato | Componente | Testes focais | Estado |
|---|---|---|---|
| Varargs tipados, prefixo fixo, aridade e seleção de candidato fixo | Frontend, Analyzer, JvmBackend, RuntimeSupport | VarargsTest | Implementado, sem validação completa |
| Posicionais nas formas separada e agrupada | Frontend, MessageSignatures | VarargsTest, MessageSyntaxTest | Implementado, sem validação completa |
| Comprehensions vetoriais e de objetos; filtros e escopos capturados | Frontend, Analyzer, JvmBackend, IterationSupport | ComprehensionTest | Implementado, sem validação completa |
| Literais compactos e acesso/remoção por operadores de coleção | Frontend, CoreLibrary, CollectionsLibrary | ParserBoundaryTest, QuotedCollectionSyntaxTest | Implementado, sem validação completa |
| Composição estrutural, aliases e campos parametrizados | ObjectModel, TypeSystem | CompositionDerivationTest | Implementado, sem validação completa |
| Composição inclui mensagens explícitas/defaults e propriedades | ObjectModel | CompositionDerivationTest | Implementado, sem validação completa |
| Introspecção e labels incluem campos compostos | CompileTimeExpander, ObjectModel | CompositionDerivationTest | Implementado, sem validação completa |
| Ord lexicográfico, Iter por campos, Vec e Block | DerivedSupport, ObjectModel, Analyzer | CompositionDerivationTest | Implementado, sem validação completa |
| Default de bound numérico concretizado por tipo primitivo | ObjectModel | CompositionDerivationTest | Implementado, sem validação completa |
| perform com tag/partes; distinção not_understood/no_applicable | Analyzer, RuntimeSupport | MessageProtocolTest | Implementado, sem validação completa |
| forward, intercept, proceed/proceed_with/redirect e preservação de bloco | Analyzer, RuntimeSupport | MessageProtocolTest | Implementado, sem validação completa |
| Proxy, target, subtipagem e ciclos | ObjectModel, TypeSystem, RuntimeSupport | MessageProtocolTest, CompositionDerivationTest | Implementado, sem validação completa |
| Privacidade em leitura dinâmica e remetente preservado | JvmBackend, RuntimeSupport | MessageProtocolTest | Implementado, sem validação completa |
| Stdlib portátil passa pelo frontend e tipagem normais | StandardPrelude, stdlib/portable.mach | LibraryIntegrationTest | Implementado, sem validação completa |
| Contratos de stdlib para números, texto, funções e controle | LibraryContracts, CoreLibrary | CoreLibraryTest | Implementado, sem validação completa |
| Coleções de base e extras imutáveis | CollectionContracts, ExtraCollectionContracts, respectivos runtimes | CollectionsLibraryTest, ExtraCollectionsTest | Implementado, sem validação completa |
| IO, formatos, datas, reflexão e utilitários síncronos | SystemContracts, UtilityContracts, respectivos runtimes | ModulesLibraryTest, UtilityLibraryTest | Implementado, sem validação completa |
| Cobertura individual de toda a referência stdlib | Biblioteca e suíte de conformidade | stdlib-conformance.md | Em desenvolvimento |
| RRB/CHAMP e demais estruturas persistentes normativas | Runtime de coleções | Adiados pelo usuário | Não iniciado |
| Concorrência, ownership e behaviors | Análise de efeitos/runtime | Adiados pelo usuário | Não iniciado |
| Divisão real e prova de divisor não zero (decisão do usuário) | Analyzer / runtime | DivisionSafetyTest | Validado por testes |
| Variância nominal nos callbacks, generics do receptor e consumo único de falha temporária | Contratos da stdlib / Analyzer / JVM | LibraryIntegrationTest | Validado por testes |
