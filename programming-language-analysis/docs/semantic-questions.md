# Questões semânticas e divergências do MachOne atual

Este arquivo registra o estado real da linguagem como implementada hoje no repositório e não substitui a especificação canônica antiga. Ele documenta as decisões operacionais adotadas pelo compilador, pelo frontend, pela análise semântica e pelo runtime.

## Estado efetivo da linguagem

MachOne hoje aceita e executa uma semântica com as seguintes escolhas já estabilizadas:

- `::` como cast bruto/forçado;
- `parse:` como conversão segura em `try[T]`;
- `maybe_parse:` como conversão opcional em `maybe[T]`;
- `unwrap` e `unwrap_or` como mecanismos de consumo de `try`/`maybe`;
- `&Any`, `&Number`, `&Textual`, `&Collection`, `&Callable`, `&Temporal`, `&Absent`, `&Failure` e tipos de usuário como forma de hierarquia nominal/abstrata;
- `&Animal` como restrição abstrata e `T & Animal` como vínculo de variável de tipo concreta;
- `vec[T]`, `vec[&Animal]`, `T & Animal` e outros tipos parametrizados em análise semântica e runtime;
- dispatch múltiplo com prioridade por correspondência exata, subtipagem abstrata e conversão autorizada;
- mensagens da stdlib e mensagens do usuário em um único mecanismo de dispatch;
- `then` e `otherwise` como açúcar sintático sobre mensagens com bloco, e `each` como mensagem normal da stdlib.

## Ambiguidades e decisões operacionais atuais

1. **Mensagem vs. açúcar sintático**
   - `then`, `otherwise` e `each` não são palavras-chave independentes do compilador; em MachOne atual eles são mensagens comuns com sintaxe amigável.
   - `condition then do ...` é reescrito, no frontend, em uma chamada de mensagem mais explícita com bloco/lambda.
   - `collection each` segue o mesmo mecanismo de envio de mensagens já usado pela stdlib e pelo código do usuário.
   - Decisão atual: o açúcar sintático existe para legibilidade, mas não define uma categoria semântica separada da linguagem.

2. **Seletores reservados e identificadores contextuais**
   - A EBNF antiga e a implementação atual divergem em relação a quais identificadores são reservados.
   - `read`, `show`, `then`, `otherwise`, `each`, assim como outros nomes usados por mensagens da stdlib, são reconhecidos em contexto de envio.
   - Decisão atual: priorizar a sintaxe funcional que já compila sobre a lista rígida de reservados da especificação antiga.

3. **Posicionais, grupos e aridade**
   - A linguagem aceita argumentos posicionais, argumentos em grupos e vetores que representam um conjunto de valores em uma assinatura.
   - Esse mecanismo é bem-sucedido em casos comuns, mas ainda depende do contexto da assinatura e da aridade detectável.
   - Ambiguidade restante: combinações complexas entre varargs, grupos posicionais e sobrecarga múltipla ainda não têm uma regra universal de resolução fora do contexto local.

4. **Cast bruto vs. conversão segura**
   - `"42"::int` devolve um `int` diretamente e falha se a conversão não for válida.
   - `("42") parse: int` retorna `try[int]` por padrão.
   - `("42") maybe_parse: int` retorna `maybe[int]` por padrão.
   - `(("abc") parse: int) unwrap` extrai o valor e exige que a falha seja tratada explicitamente em um fluxo de erro.
   - Decisão atual: cast bruto e conversão segura são semânticas distintas; a linguagem não mistura as duas em um único operador.

5. **Hierarquia numérica e subtipos**
   - A implementação atual usa uma hierarquia nominal de números com as seguintes noções principais: `&Number` > `&Real` > `&Integer` / `&Decimal`, com `&Signed` e `&Unsigned` abaixo de `&Integer` e tipos concretos como `byte`, `short`, `int`, `long`, `ubyte`, `ushort`, `uint`, `ulong`, `float` e `double`.
   - `&Animal` pertence à categoria de tipos abstratos restritos, e `T & Animal` preserva a identidade concreta de um único subtipo por vinculação de `T`.
   - `&Any` funciona como restrição universal e serve como ponto de entrada da cadeia de subtipos.
   - Limitação atual: a generalização completa de bounds para todos os tipos parametrizados e casos complexos ainda depende de análise mais fina no frontend e no type system.

6. **Multiple dispatch**
   - A ordem atual de resolução do dispatch é: correspondência exata, subtipagem abstrata, conversões autorizadas.
   - Quando dois candidatos são incomparáveis, a linguagem diagnostica ambiguidade em vez de tentar escolher por ordem de declaração.
   - Decisão atual: o dispatch não depende da ordem textual dos métodos; ele depende da especificidade e do tipo do receptor e dos argumentos.

7. **`do`/blocos e retorno**
   - `return` em um bloco `do` resolve para o destino lexical mais próximo, normalmente o método ou corpo que o gerou.
   - Se esse destino já foi encerrado, o compilador deve lançar `invalid_return`.
   - A especificação original não define esta regra de forma completa para todos os casos de closure, então a implementação usa a regra lexical como decisão operacional válida.

8. **`nil`, `Fail`, `try` e `maybe`**
   - Esses tipos continuam distintos na semântica atual: `nil` representa ausência; `Fail` representa falha explícita; `try` e `maybe` encapsulam o resultado de conversões e operações parcialmente seguras.
   - O consumo de valores resultantes ainda depende do fluxo de tratamento de erro do código e do uso de `unwrap`, `unwrap_or`, `or_else` ou `rescue`.
   - O documento antigo não resolveu completamente todas as regras de falha ignorada em coleções e retornos.

9. **Coleções heterogêneas e homogêneas**
   - `vec[&Animal]` representa coleção de valores que respeitam a restrição `&Animal`.
   - `vec[T & Animal]` e variações com `T` vinculado preservam identidade concreta quando um tipo concreto é escolhido.
   - A linguagem usa o critério de homogeneidade para coleções gerais e também aplica restrições por subtipo e bind de tipo.
   - Ambiguidade restante: a verificação exata de coleções de tipos heterogêneos ainda depende de regras de inferência e de expressividade do tipo parâmetro.

10. **Persistência, records e composição**
   - `use T as nome` e composições estruturais continuam sendo parte da semântica atual.
   - O runtime e o frontend tratam records, abstract data, composition e primitivas de coleção como entidades distintas, mas algumas regras de aliasing, reparenting e cópia persistente ainda exigem formalização adicional.
   - Isso não invalida a implementação atual, mas sinaliza que a modelagem de mutabilidade e ownership ainda não está fechada de forma canônica.

11. **Unicode, texto e char**
   - `char` é Unicode; `str` é representado por pontos de código Unicode; `bytes` expõe o conteúdo UTF-8.
   - O modelo atual não define completamente todos os casos de grapheme cluster, normalização e segmentação por visual glyph.
   - Decisão atual: seguir code point como unidade básica do texto.

12. **Metaprogramação e compile time**
   - O compilador aceita avaliação segura em compile time para trechos puramente determinísticos.
   - Ele não executa código arbitrário de IO ou efeitos colaterais no momento da compilação.
   - Esse limite ainda é uma decisão operacional e não um sistema completo de metaprogramação.

13. **Biblioteca padrão e APIs incompletas**
   - Há recursos de stdlib já usados e testados em máquinas reais, mas partes da biblioteca continuam prototipadas, documentadas em forma de proposta ou em processo de validação.
   - Exemplos como `Socket`, `Ring`, `Locale`, `Color`, `Graph`, `Matrix` e outros continuam dependendo de contratos mais específicos para serem declarados “conformes”.
   - Decisão atual: a implementação não trata essas APIs como totalmente finalizadas se elas ainda não têm assinatura, comportamento e testes consistentes.

14. **Documentação canônica vs. implementação real**
   - A especificação antiga e a implementação atual continuam divergindo em vários pontos.
   - O que o compilador aceita hoje é a base operacional da linguagem, mesmo quando isso contradiz trechos do documento normativo antigo.
   - Esse arquivo se aproxima da realidade atual do MachOne executável e não pretende impor uma nova especificação completa sem uma revisão humana formal.

## Decisões que já foram ratificadas pelo compilador

- Envios com `:` são aceitos de formas mistas; o seletor permanece o mesmo.
- `show` e `read` são mensagens/identificadores e não “palavras mágicas” separadas do dispatch.
- `then` e `otherwise` são açúcar sintático sobre mensagens com bloco.
- `each` é um seletor de mensagem regular da stdlib.
- `::` é cast bruto; conversão segura é `parse:`/`maybe_parse:`.
- `&Any` atua como restrição universal.
- `&Animal` e `T & Animal` são aceitos e tratados com identidade concreta vinculada.
- O dispatch múltiplo prioriza exata, depois abstrata, depois conversão autorizada.
- A hierarquia numérica oficial do runtime inclui tipos signados/unsigned, reais e decimais.

## Pendências que continuam abertas

- norma completa para todas as coleções persistentes e custo de operação;
- regras formais de ownership e aliasing para mutáveis compartilhados;
- definição final das APIs adicionais da stdlib e seus contratos;
- modelagem completa de efeitos em compile time e metaprogramação;
- resolução formal de casos ambíguos de dispatch e aridade em combinações complexas.

Este documento deve ser atualizado sempre que a linguagem mudar de comportamento. Ele registra o MachOne que hoje compila e executa, e não a versão hipotética da especificação perfeita.