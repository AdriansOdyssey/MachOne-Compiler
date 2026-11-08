# Questões semânticas e divergências

Nenhuma das questões abaixo autoriza alterar a especificação. Recursos afetados permanecem pendentes quando não há interpretação dedutível.

1. **Seletores reservados.** EBNF 1.2/2.11 exclui `read` de Identifier, mas 14_abstract_data define `on read` e `(ssd) read`. I1 resolve explicitamente `to/by`, não este caso. `type` é contextual e aparece como chave em `%{type: ...}`. Decisão do usuário: priorizar a sintaxe dos exemplos. O parser deve permitir seletores contextuais usados nos exemplos, incluindo read, sem alterar o original EBNF.
2. **Posicionais.** 10_methods define `on final_price: [base, discount, tax]` e chama com três argumentos separados. O @doc final exige chamada `final_price: [100.0, 10.0, 0.1]`, indistinguível de um único vetor. É necessário escolher se há unpack implícito/contextual. Não implementado.
3. **Conversão total.** 02_types diz `42::long` sem Fail quando sempre possível; 19 §12 diz conversão sempre `try[T]`; 19 §13 rejeita atribuir try[int] a int. Deve-se separar conteúdo de sucesso de tipo estático, mas a exceção para conversões totais precisa de confirmação.
4. **Fechamento de diretivas.** EBNF exige @end para @for; 19 §7 usa end após @for level. Pela decisão do usuário, aceitar o fechamento end presente no exemplo, quando a implementação de diretivas estiver disponível.
5. **Retorno de do.** 09_errors diz return em do retorna do método, e lambda com barras retorna da lambda. 02_types permite do ligado a um binding e depois executado. Falta definir o alvo de return desse bloco quando criado fora de método ou quando o método criador já retornou.
6. **OMOP e persistência.** 06 exige CHAMP imutável, mas deposit altera balance em lambda capturada e espera consultar o objeto copiado. É preciso formalizar rebinding do receptor/célula e captura/reparenting ao copiar, sem mutar a árvore persistente anterior.
7. **Behaviors.** 17 declara impl int.plus e behavior int.plus, depois proíbe o envio síncrono. Falta formalizar substituição entre registros impl/behavior e a ordem/serialização de tarefas para o mesmo receptor. O padrão tell/ask depende dessa ordem para o exemplo produzir 10.
8. **Erros no exemplo 17.** `res_inbbox` é declarado, `res_inbox` é usado. `actor_ex` é capturado antes de declaração; um outbox[int] anterior é consumido como obj; inbox/outbox são redefinidos depois. Não aceitar a seção inteira como programa válido sem separar/corrigir esses casos por decisão do autor.
9. **Exemplos independentes.** EBNF Program declara cada seção independente, mas vários arquivos reutilizam bindings/records de seções anteriores (02 usa example; 15 usa Account). Harness deve distinguir fragmentos, programas completos e exemplos negativos, sem inserir valores fictícios.
10. **Falhas ignoradas.** 09 exige erro se um Fail for ignorado, incluindo fim de programa. É necessário definir consumo de try quando o resultado é ligado mas nunca usado e o comportamento de Fail dentro de coleções.
11. **Metaprogramação e efeitos.** 12 usa `let x = 10` no @if. Falta um limite normativo para avaliação de métodos/IO/recursão em compile time. Não executar código de IO do programa durante compilação.
12. **Custo de concatenação.** 05 exige RRB e concatenação O(log n); 19 §12 anota `items + [5]` O(n). A estrutura RRB é exigência explícita de 05; a anotação de custo é divergente.
13. **Overflow e promoção.** Decisão explícita do usuário: operações inteiras fazem wrap na largura do tipo, preservando o tipo. Literais e conversões continuam verificando faixa. Há testes para todas as larguras, underflow e potência modular. A promoção entre tipos signed/unsigned diferentes e expoentes inteiros negativos ainda não tem uma tabela normativa completa.
14. **Ambiguidade de multiple dispatch.** A especificação diz mais específico vence, mas não define candidatos incomparáveis. O runtime detecta e relata ambiguidade; é necessário ratificar o diagnóstico estático/runtime.
15. **Ownership de aliases.** Captura/passagem de outbox move a posse; faltam regras para cópia indireta via record/obj/closure, branches, retorno de empréstimos e alias de referência mutável compartilhada. Essas verificações não podem ser substituídas por um lock runtime.

As decisões I1–I4 e precedência da EBNF são dados normativos do documento fornecido; ainda há inconsistência interna na tabela que lista prefixo acima de potência mas descreve -2**2 como -(2**2). A regra formal Unary/Pow e o exemplo apontam para potência mais forte.


## Decisões desta etapa

* Envios aceitam `:` em cada parte de forma totalmente opcional; declarações com parâmetros mantêm `:`. As quatro formas mistas denotam o mesmo seletor.
* `show` e `read` são mensagens/identificadores, conforme determinação expressa do usuário, prevalecendo sobre a lista Reserved da EBNF original.
* Tipagem contextual de literais tem prioridade. Sem contexto, decimais inteiros positivos usam int, long ou ulong conforme a faixa necessária. Essa regra de implementação torna executáveis os valores documentados sem introduzir bigint como tipo da linguagem.
* Heredoc preserva o texto entre as linhas delimitadoras, inclusive a quebra final; os tags de abertura e fechamento devem coincidir. A EBNF não consome claramente a quebra antes do fechamento, e não há exemplo com saída normativa para esse detalhe.
* Abstract Dispatches são concretizados e checados em cada record. Abstract data com corpo não vazio permanece rejeitado: a EBNF permite Body, mas os exemplos normativos não definem o significado de métodos nesse corpo.
* Defaults de abstracts/bounded impls não substituem uma implementação concreta equivalente. Candidatos conflitantes sem escolha por especificidade são diagnosticados. Essa escolha está explicitada para não ocultar a ausência de uma regra detalhada de conflitos na documentação.
* Return de um bloco do usa o destino lexical envolvente. Uma tentativa após o encerramento desse destino recebe diagnóstico invalid_return; o texto original não define retorno para um frame já encerrado.
* A sintaxe de acesso a campo continua `p's #nome`. O teste sem # foi retirado pelo próprio usuário; chaves bare continuam referências a valores, como nos algoritmos de objetos.

Questões já operacionalizadas em casos testados (rescue, metaprogramação, aliases e blocos) continuam com seus limites registrados em implementation-status.md. Decisões pendentes não devem ser apresentadas como semântica integral validada.

## Decisões para objetos, mensagens e stdlib — 0.3

* **Estruturas persistentes:** `docs/persistent-structures.md` define a semântica provisória para `RRB-Vector` e `CHAMP` e marca explicitamente as decisões ainda pendentes de revisão humana. A implementação atual do runtime segue esse desenho sem afirmar que a prova formal de custo ou a conformidade normativa completa foi alcançada.
* **Composição — resposta explícita do usuário:** `use T as nome` incorpora os campos e as mensagens ao record final. O alias agrupa argumentos de construção em `(nome) has ...`; não cria um componente acessível separado. A importação automática das derivações nativas de um componente, além de suas propriedades, ainda não está totalmente especificada.
* **Derivações — resposta explícita do usuário:** `Ord` compara campos lexicograficamente; `Iter` percorre campos na ordem declarada; `Block` executa o único campo lambda. Zero ou múltiplos campos lambda em `Block` são erros.
* **Divisão — resposta explícita do usuário em 3/11/2026:** `/` retorna `try[double]` quando o divisor puder ser zero. Literais não nulos, bindings conhecidos e condições de não nulidade numérica permitem resultado `double`; `DivisionSafetyTest` exercita essas provas. Zero, inclusive `-0.0`, produz `Fail #zero_division`. As assinaturas originais que dividem por parâmetros sem guard/tratamento precisam agora declarar `try` ou tratar a falha; os arquivos originais permanecem intactos. A prova atual não é interprocedural.
* **`get_element` — divergência:** o original `06_omop.ma` mostra retorno do valor (`Carlos`); a nova referência stdlib §10 menciona objeto de um par. Mantém-se o valor, seguindo a decisão anterior do usuário de priorizar exemplos. `element_at` retorna o par.
* **Remoção em objetos — divergência:** os exemplos originais removem por valor; a nova referência menciona chaves. `delete`/`\\` removem valores; `delete_key`/`except` removem chaves. Não se alteram silenciosamente os exemplos antigos.
* **Texto Unicode — decisão operacional documentada:** `char`, índices e `length` de strings usam pontos de código Unicode, não bytes nem grafemas. `bytes` expõe UTF-8. A referência sugere caracteres mas deixa a segmentação em aberto.
* **Varargs e posicionais:** `...xs T` recebe `vec[T]`; um grupo posicional aceita argumentos separados ou vetor literal quando a assinatura identifica um grupo. Varargs permanecem finais. A resolução contextual ainda tem limites em combinações ambíguas de aridades.
* **Propostas da stdlib:** [P] foi incluído no escopo pelo pedido do usuário, mas nomes sem assinaturas/semântica suficientes (por exemplo Socket, Ring e aspectos de Locale/Color/Graph/Matrix), carregamento de módulos e doctests ainda precisam de contratos e implementação. A ausência não é contabilizada como conformidade.
* **JSON:** `generate` segue o retorno `str` proposto para dados serializáveis. Valores internos sem representação JSON, como NaN ou records dentro de `obj[any]`, causam erro de codificação; a referência não define um protocolo recuperável para esses valores.
* **Construtores de coleções extras:** Dictionary/SortedMap construídos de vetores de pares retornam `try`, porque `vec[vec[any]]` não prova estaticamente o tamanho e os tipos dos pares. Construção vazia ou com mapa tipado tem contrato separado.
