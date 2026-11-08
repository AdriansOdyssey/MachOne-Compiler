# Estruturas persistentes do MachOne

Este documento registra a semântica provisória para os tipos persistentes do runtime síncrono. Ele foi escrito para reduzir a ambiguidade antes da implementação final da camada de coleções, sem alterar os arquivos preservados em `specification/`.

Todas as decisões abaixo são provisionais e exigem revisão humana quando a especificação ou exemplos normativos forem mais específicos. O runtime deve marcar claramente qualquer compromisso que ainda não seja uma prova formal de conformidade.

## 1. RRB-Vector (`vec[T]`)

### 1.1. Modelo

- `vec[T]` é uma sequência imutável indexada por inteiro.
- Cada operação que produz uma nova sequência retorna uma nova raiz, sem mutar versões antigas.
- A estrutura segue a ideia de RRB (Relaxed Radix Balanced) com ramificação 32 (`5` bits por nível), tail de até 32 elementos e compartilhamento estrutural por path copying.
- O contêiner usa uma árvore de nós internos e um tail prefixado para reduzir a sobrecarga de concatenação e atualização.

### 1.2. Invariantes

- O número de elementos deve ser consistente com a soma dos tamanhos dos nós e do tail.
- A profundidade da árvore deve crescer como `O(log_32 n)`.
- Níveis internos nunca devem ficar vazios.
- O tail nunca pode exceder `32` elementos.
- O valor de `slice`, `concat`, `update` e `append` deve preservar a ordem de índice.
- A estrutura final deve ser canônica para um dado conteúdo: duas sequências com o mesmo conteúdo observável têm a mesma representação observável, mesmo que a árvore interna varie.

### 1.3. Custos esperados

- indexação: `O(log_32 n)`
- update: `O(log_32 n)`
- append/prepend: `O(1)` amortizado via tail/head
- concat: `O(log n)` por nível de árvore, com uso de tail para reduzir cópias
- slice/take/drop: `O(log n)` para atender a faixa, com cópia mínima dos nós necessários
- iteração: `O(n)` em ordem de índice

### 1.4. Igualdade e hash

- Igualdade é estrutural por elementos em ordem.
- Dois vetores são iguais se tiverem o mesmo tamanho e os elementos correspondentes forem iguais pela igualdade da linguagem.
- O hash do vetor é uma combinação ordenada dos hashes dos elementos, e deve ser consistente com a igualdade. Ele não deve depender da forma interna da árvore.

### 1.5. Política de slice

- `slice`, `take` e `drop` devem produzir uma nova sequência com os elementos selecionados e sem reter referências a elementos fora do intervalo além do mínimo necessário.
- Quando houver excesso de retenção por “partes” da árvore antiga, o runtime deve compactar o intervalo resultante em uma subárvore ou em um vetor compacto novo.
- Regra prática: qualquer pedaço resultante deve manter apenas os elementos do intervalo e as referências compartilhadas estritamente necessárias para o compartilhamento persistente.

### 1.6. Erro de índice

[DECISÃO PROVISÓRIA]

- Índices fora do limite devem produzir `Fail` com a tag de índice já existente na stdlib (por exemplo, `#index` ou `#range` conforme o protocolo do runtime atual).
- O runtime não deve lançar `IndexOutOfBoundsException` diretamente para o código MachOne; exceções Java são convertidas em falha de domínio/índice na camada de adaptação.

## 2. CHAMP (`obj[T]` / mapas e objetos)

### 2.1. Modelo

- O mapa/objeto persistente usa uma trie bitmap de 32 vias (CHAMP-like).
- O nó possui dois mapas de bit (`dataMap` e `nodeMap`):
  - `dataMap`: entradas inline com chaves e valores diretamente no nó
  - `nodeMap`: subnós para os bits que apontam para profundidade maior
- O layout interno separa entradas de subnós para facilitar a forma canônica e reduzir o custo de atualização.
- Colisões totais de hash ficam em nós de colisão no nível máximo.

### 2.2. Invariantes

- O bitmap deve refletir exatamente quais slots estão ocupados.
- O índice do slot para um hash deve ser derivado do bit correspondente ao nível atual.
- `remove` deve recolher subnós com uma única entrada para o pai, preservando a forma canônica.
- Atualizações não podem vazar nós nem quebrar a estrutura de bitmaps.
- Uma mesma coleção de entradas deve produzir sempre a mesma forma canônica, mesmo que as operações tenham acontecido em ordem diferente.

### 2.3. Operações esperadas

- `get`: `O(log_32 n)` esperado
- `put`: `O(log_32 n)` esperado
- `remove`: `O(log_32 n)` esperado
- `contains`: `O(log_32 n)` esperado
- `merge` / `|`: custo proporcional ao menor lado e compartilhamento estrutural dos caminhos divergentes

### 2.4. Igualdade e hash

- Igualdade é extensional: duas coleções são iguais quando têm as mesmas chaves, e cada chave mapeia para um valor igual pela igualdade da linguagem.
- A ordem de inserção não deve afetar a igualdade.
- O hash da coleção é independente da ordem e consistente com a igualdade. É calculado a partir das entradas misturadas e em uma ordem determinística, sem depender de identidade nem de semente aleatória.
- Isso evita que duas coleções equivalentes produzam hashes diferentes apenas por ordem de construção.

### 2.5. Ordem de iteração

[DECISÃO PROVISÓRIA]

- A ordem de iteração do objeto/mapa deve ser determinística e depender apenas do conteúdo, não da ordem de inserção.
- Isso é coerente com a noção de `obj[T]` como mapa funcional e reduz bugs em igualdade/hash.
- Se a especificação ou os exemplos OMOP exigirem ordem de inserção como valor semântico, será necessário usar uma variante ordenada com vetor persistente de chaves para preservar estável a ordem de iteração. Essa variante tem custo extra e deve ser declarada explicitamente.

### 2.6. Records e objetos imutáveis

[DECISÃO PROVISÓRIA]

- Records e objetos imutáveis usam CHAMP quando se comportam como mapas de campos.
- `self = self | {...}` produz uma nova versão por merge, com o lado direito vencendo em chaves duplicadas.
- A referência antiga continua válida e não é modificada.
- Records com campos mutáveis continuam sendo identidade própria e não são cobertos por essa regra, a menos que a semântica do record mutável seja definida por outra camada.

### 2.7. Ação de remoção e reinserção

- `remove` e `put` repetidos não podem vazar nós nem produzir estruturas inconsistentes.
- A forma canônica deve ser preservada após qualquer sequência de inserir/remover/reinserir.

## 3. Observações sobre implementação

### 3.1. Estruturas do runtime

A implementação do runtime deve separar classes concretas por sua responsabilidade, por exemplo:

- `RrbVector`
- `RrbNode`
- `RrbTail`
- `ChampMap`
- `ChampSet`
- `ChampNode`
- `ChampCollisionNode`

Essas classes devem ser implementadas como estruturas próprias do runtime e não como embrulho de `java.util.List`, `java.util.Map` ou `LinkedHashMap`.

### 3.2. Verificação de invariantes

Cada estrutura deve expor um `checkInvariants()` usado em testes, cobrindo:

- tamanho e profundidade
- tamanho do tail
- bitmap e índice corretos
- forma canônica do CHAMP após remoção
- consistência da igualdade/hash com o conteúdo observado

### 3.3. Adaptadores nas bordas

Interoperabilidade com Java só deve existir em camada de borda; por exemplo:

- converter `RrbVector` em `List` somente quando for necessário para o runtime ou para uma chamada específica
- converter `ChampMap` em `Map` somete quando o código Java exigir isso explicitamente
- nunca ocultar a estrutura persistente sob um `LinkedHashMap` como implementação real

## 4. Contratos de performance e testes

### 4.1. Testes de contadores

Os testes devem evitar depender de tempo de parede e, em vez disso, contar:

- nós visitados
- nós copiados
- profundidade de percurso
- número de comparações do hash
- quantidade de materiais compartilhados vs. copiados

### 4.2. Testes de propriedade

- Usar uma semente fixa de PRNG.
- Gerar uma sequência de operações sobre vetor ou mapa.
- Comparar com um modelo de referência simples (`ArrayList`/`HashMap`) usado apenas dentro do teste.
- Registrar a semente em caso de falha.

### 4.3. Persistência

- Para cada sequência longa de operações, guardar as versões intermediárias.
- Verificar que todas as versões anteriores continuam estáveis e que a operação atual não altera referências antigas.

### 4.4. Casos de borda

- vazio
- tail cheio
- tamanhos em `32`, `33`, `1024`, `1025`, `32768`, `32769`
- concatenação de vetores grandes e relaxados
- slice em começo, fim e meio
- remoção e reinserção repetidas
- colisões de hash forçadas
- mapa com muitas chaves em teste lento marcado

## 5. Decisões provisórias e ambiguidades pendentes

- `RRB` e `CHAMP` continuam sendo provisionais até que o projeto confirme a semântica completa dos exemplos normativos e os testes de conformidade.
- O custo efetivo de concatenação e de atualização em `RRB` pode variar conforme a política de relaxamento e de compactação do tail; este documento aponta para a estratégia mais coerente com os documentos e o objetivo da linguagem.
- Se a especificação OMOP exigir ordem de inserção como parte do protocolo de objeto, a variante ordenada do CHAMP deve ser introduzida e documentada.
- O projeto ainda precisa definir e validar como a linguagem representa ponteiros/aliases, `self` e reparenting em registros mutáveis. Isso não é resolvido por este documento.

## 6. Status

Este documento formaliza a proposta provisória e o contrato de implementação para a próxima fase do runtime. Ele não declara que a especificação completa foi implementada nem que a complexidade foi provada formalmente; isso continua sendo trabalho de validação e revisão humana.
