# Biblioteca padrão MachOne

A biblioteca padrão começa pelos contratos já definidos nos documentos de MachOne. Este diretório registra a fronteira entre serviços da JVM, operações fundamentais da linguagem e código que poderá ser escrito em MachOne. Sua existência não significa que todas as APIs abaixo já estejam implementadas.

As decisões e a ordem de implementação estão em [docs/stdlib-roadmap.md](../docs/stdlib-roadmap.md). O estado executável de cada recurso continua sendo descrito pela matriz de conformidade e pelos testes do projeto.

## Fronteira de implementação

| Camada | Responsabilidade |
|---|---|
| Compilador | Tipos estáticos, `maybe[T]`, `try[T]`, resolução de seletores e aridades, `rescue`, operadores, acesso a campos, composição de abstracts, labels e expansão de código. |
| Runtime | Representar valores/Fail/metadados; serviços que dependem da JVM, como entrada/saída, armazenamento das coleções e agendamento/canais quando forem implementados. |
| Biblioteca em MachOne | Operações derivadas expressas com métodos, records, abstracts e lambdas existentes: algoritmos, agregações e padrões de aplicação. |

`readable`, `accessible`, `configurable` e labels de domínio continuam parte do modelo de records e metaprogramação. A biblioteca não introduzirá labels de banco de dados ou frameworks no núcleo. `tell` e `ask` continuarão seletores comuns de um padrão de atores; não serão palavras-chave.

## Primeiro conjunto de contratos normativos

* Valores e tipos: `type`, `isa`, teste de subtipo `&`, conversão `::`, igualdade forte, semelhança e `stringify`.
* Falhas e ausência: `Fail`, `raise`, `kind`, `ok?`, `fail?`, `rescue`, `maybe[T]`, `try[T]` e `?:`.
* Entrada/saída: `show` e `read`.
* Strings e números: interpolação, formatação decimal, `upcase`, `even?`, `abs`, operadores numéricos e ranges.
* Vetores e objetos: consultas, cópias persistentes, atualizações, iteração e transformação, preservando as estruturas RRB/CHAMP exigidas pela especificação.
* Lambdas: `result`, captura e retorno; algoritmos de ordem superior usam esses mesmos mecanismos.
* Objetos e mensagens: `new`, `initialize`, `create`, introspecção e o protocolo de mensagem definido em `15_message_programming.mach`.
* Concorrência futura: IOBox/inbox/outbox, tasks e Time, obedecendo ownership e ciclo de vida normativos.

Os seletores existentes aceitam `:` opcional nos envios conforme a orientação do usuário; isso não altera seu nome, aridade ou contrato de tipos. As declarações de métodos continuam usando a sintaxe definida com `:` para parâmetros.

A especificação ainda não define um sistema de importação de bibliotecas nem um formato de publicação de pacotes. Não foi criada uma palavra-chave `import` ou uma convenção de namespaces como se já fossem parte da linguagem. O carregamento de arquivos da biblioteca será decidido explicitamente antes da primeira biblioteca MachOne distribuída separadamente.
