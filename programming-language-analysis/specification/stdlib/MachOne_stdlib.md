# MachOne — Biblioteca Padrão (std lib)

Referência completa das mensagens, tipos e módulos da biblioteca padrão da MachOne.

> **Como ler este documento**
>
> - **[S]** = definido ou usado nos 19 arquivos da especificação (`01_basic` … `19_philosophy`). É o contrato que já existe.
> - **[P]** = *proposto*. Inferido de Ruby, Smalltalk, Self, Objective-C, Erlang/Elixir, Go e Rust, e adaptado ao modelo da MachOne. Está aqui para você aprovar, renomear ou cortar.
> - Quando uma mensagem tem os dois, vale a marca da forma que aparece na spec.
>
> **Notação de assinatura:** `(receptor) seletor: arg arg2 -> Retorno`.
> `T` é genérico. `vec[T]`, `obj[T]`, `maybe[T]`, `try[T]` seguem a spec.
> `⟨bloco⟩` é um lambda passado por `|x| { … }`, `|x| do … end` ou `=> |x|`, e `&it` é o argumento implícito.
> Mensagens sem argumento são unárias: `(x) upcase`.

---

## 0. Princípios da biblioteca

1. **Tudo é mensagem.** `show`, `read`, `then`, `each`, `+` são seletores. Operadores são `op` (`(a) +: b` ≡ `a + b`) **[S]**.
2. **Valores são imutáveis e persistentes.** Vetores (RRB-Vector) e objetos (CHAMP) devolvem *novas* estruturas que compartilham memória **[S]**. Não existem métodos "bang" (`sort!`): `(v) sort` devolve um novo vetor. Quem muda estado são variáveis `@x` e records via `self = self | {...}`.
3. **Despacho múltiplo.** A stdlib é escrita como `impl` por tipo, e o método mais específico vence **[S]**.
4. **Duas formas de "não deu certo":**
   - `maybe[T]` + `?:` → *ausência esperada* (`nil`).
   - `try[T]` + `rescue` / `fail?` → *falha com motivo* (`Fail` com tag) **[S]**.
5. **Convenções de nome**
   - `?` no fim → predicado que devolve `bool` (`even?`, `has?`, `empty?`) **[S]**.
   - `copy_with` → cópia com alteração **[S]**.
   - `maybe_x` → versão segura de `x`, que devolve `nil` em vez de `Fail` (`maybe_get`, `maybe_slot`) **[S]**.
   - Conversão: `::tipo` (`"42"::int`) devolve `try[T]` **[S]**; `to_x` só para conversões que nunca falham **[P]**.
   - Índices começam em `0`; `-1` é o último elemento **[S]**.
   - Ranges: `a to b` exclusivo, `a..b` inclusivo **[S]**.
6. **Tags como vocabulário.** Chaves de objeto, motivos de `Fail` e seletores dinâmicos são tags (`#ok`) **[S]**.
7. **Camadas.** *Prelude* (sempre carregada) → *core* (importável por padrão) → *extras* (módulos opcionais). A seção 21 mostra a divisão.

---

## 1. Tipos embutidos e hierarquia

| Tipo | Descrição | |
|---|---|---|
| `any` | supertipo de tudo | [S] |
| `nil` | ausência de valor; só existe como `maybe[T]` | [S] |
| `bool` | `true`, `false` | [S] |
| `byte` `short` `int` `long` | inteiros com sinal (8/16/32/64 bits) | [S] |
| `ubyte` `ushort` `uint` `ulong` | inteiros sem sinal | [S] |
| `float` `double` | ponto flutuante (32/64 bits) | [S] |
| `char` | um caractere (`` `A` ``) | [S] |
| `str` | string UTF-8 imutável | [S] |
| `tag` | identificador internado (`#ok`) | [S] |
| `type` / `type[T]` | tipo como valor | [S] |
| `vec[T]` | vetor homogêneo (`vec[any]` para misto) | [S] |
| `obj[T]` | mapa de tags para valores (`obj[any]` por padrão) | [S] |
| `lambda[[A, B] R]` | função / bloco | [S] |
| `maybe[T]` | `T` ou `nil` | [S] |
| `try[T]` | `T` ou `Fail` | [S] |
| `fail` | valor de falha | [S] |
| `union[A, B]` | união de tipos | [S] |
| `task[A…, R]` | tarefa assíncrona | [S] |
| `inbox[T]` / `outbox[T]` | extremos de canal | [S] |
| `IOBox[T]` | fábrica de par inbox/outbox | [S] |
| `message` | mensagem reificada (`msg's #name`) | [S] |
| `Number` | supertipo numérico (`float & Number`) | [S] |
| `Integer`, `Signed`, `Unsigned`, `Real` | níveis intermediários | [P] |
| `Collection`, `Sequence`, `Mapping` | supertipos de coleções | [P] |
| `Comparable`, `Enumerable`, `Iterable` | `abstract` usados pelas derivações `Ord` e `Iter` | [P] |
| `Duration`, `Instant`, `Date`, `DateTime` | tempo | [P] |
| `Range` | `1..5`, `1 to 6 by 2` | [P] |
| `Set[T]`, `Dictionary[K, V]`, `Queue[T]`, `Stack[T]`, `Deque[T]`, `Pair[A, B]` | coleções extras | [P] |

**Hierarquia sugerida** (via `abstract data`):

```
any
├── Number
│   ├── Integer ── Signed (byte short int long) · Unsigned (ubyte ushort uint ulong)
│   └── Real ───── float · double
├── Collection
│   ├── Sequence ── vec[T] · Range · str · Deque[T]
│   └── Mapping ─── obj[T] · Dictionary[K, V]
├── bool · char · tag · type · nil
├── lambda · message · fail
└── task · inbox · outbox
```

---

## 2. Prelude (sempre disponível, receptor implícito)

Mensagens que podem ser enviadas sem receptor explícito.

| Seletor | Assinatura | Descrição | |
|---|---|---|---|
| `show` | `show x -> nil` | imprime `x` (via `stringify`) com quebra de linha | [S] |
| `read` | `read prompt -> str` | imprime o prompt e lê uma linha da entrada padrão | [S] |
| `print` | `print x -> nil` | imprime sem quebra de linha | [P] |
| `eprint` | `eprint x -> nil` | imprime na saída de erro | [P] |
| `ask` | `ask prompt -> str` | sinônimo de `read` | [P] |
| `exit` | `exit: code int` | encerra o programa | [P] |
| `sleep` | `sleep: (Duration)` | suspende a task atual | [P] |
| `assert` | `assert: cond, msg: text -> nil` | falha se `cond` for falso | [P] |
| `loop` | `loop ⟨bloco⟩` | repete até `break` | [S] |
| `spawn` | `spawn ⟨bloco⟩ -> task[...]` | cria task | [S] |
| `fail` | `(Fail) raise tag, msg` | cria e lança falha (ver seção 11) | [S] |

> `show`, `read`, `spawn`, `loop` são palavras da gramática (`PrefixCommand`). Se você preferir que sejam mensagens comuns da prelude, remova-as da lista de reservadas.

---

## 3. Object (mensagens que todo valor entende)

Definido em `impl any`.

| Seletor | Assinatura | Descrição | |
|---|---|---|---|
| `type` | `(x) type -> type` | tipo concreto do valor | [S] |
| `isa` (op) | `x isa T -> bool` | pertence ao tipo? | [S] |
| `&` (op) | `T & S -> bool` | `T` é subtipo de `S`? | [S] |
| `==` `!=` | `a == b -> bool` | igualdade; por padrão compara identidade, sobrescreva com `eq` ou `op ==` | [S] |
| `eq` | `(a) eq: b -> bool` | igualdade estrutural (usada pela derivação `Eq`) | [S] |
| `like` `~` | `a like b -> bool` | semelhança: compara valores, não tipos | [S] |
| `stringify` | `(x) stringify -> str` | representação em `str`; usada por `show`, `$x` e coleções; sobrescrevível | [S] |
| `inspect` | `(x) inspect -> str` | representação de depuração (`Person{name: "Ana"}`) | [P] |
| `copy` | `(x) copy -> T` | cópia rasa (O(1) em coleções) | [S] |
| `hash` | `(x) hash -> long` | hash estável (base de `Set` e `Dictionary`) | [P] |
| `then` / `otherwise` | em `bool`, ver seção 7 | | [S] |
| `nil?` | `(x) nil? -> bool` | é `nil`? | [P] |
| `present?` | `(x) present? -> bool` | não é `nil` nem vazio | [P] |
| `tap` | `(x) tap ⟨bloco⟩ -> T` | executa o bloco e devolve `x` | [P] |
| `then_do` | `(x) then_do ⟨bloco⟩ -> R` | aplica o bloco a `x` e devolve o resultado (`yield_self`) | [P] |
| `responds_to?` | `(x) responds_to? tag -> bool` | entende a mensagem? | [S] |
| `perform` | `(x) perform: tag with args -> try[any]` | envio dinâmico | [S] |
| `slot` / `maybe_slot` | `(x) slot tag -> any` | lê atributo direto (ignora `op 's`); `maybe_slot` devolve `nil` | [S] |
| `slots` | `(x) slots -> vec[tag]` | nomes dos atributos | [P] |
| `frozen?` | `(x) frozen? -> bool` | sempre `true` (tudo é imutável) | [P] |

### Hooks de mensagem (o que um tipo pode sobrescrever) — pilares do OMOP **[S]**

| Hook | Assinatura | Papel |
|---|---|---|
| `initialize` | `on initialize: …` | prepara o estado dentro de `new` |
| `forward` | `on forward: msg -> maybe[any]` | pilar 2: devolve o novo receptor ou `nil` |
| `intercept` | `on intercept: msg -> try[any]` | pilar 3: vê toda mensagem externa |
| `target` | `on target` | pilar 4 (Proxy): alvo a cada mensagem |
| `stringify` | `on stringify -> str` | texto de `show`/interpolação |
| `eq` | `on eq: other` | igualdade estrutural |
| `op ⟨sel⟩` | `on op +: other T` | operadores |
| `op 's` | `on op 's: key tag` | acesso seguro customizado |
| `value` | derivação `Block` | `(obj) value` |
| `each` | derivação `Iter` | iteração |
| `<=>`/`compare` | derivação `Ord` | ordenação |

### Mensagem reificada (`message`) **[S]**

| Seletor | Devolve | |
|---|---|---|
| `msg's #receiver` | receptor | [S] |
| `msg's #name` | tag do seletor | [S] |
| `msg's #parts` | `vec[tag]` das partes | [S] |
| `msg's #args` | `vec[any]` | [S] |
| `msg's #block` | bloco anônimo ou `nil` | [S] |
| `(msg) proceed` | atende normalmente | [S] |
| `(msg) proceed_with args` | atende trocando os argumentos | [S] |
| `(msg) redirect other` | envia a `other` | [S] |

---

## 4. Tipos e metaprogramação (`type`, `&attrs`)

| Seletor | Assinatura | Descrição | |
|---|---|---|---|
| `(T) new` / `new: args` | `-> T` ou `try[T]` | fábrica: `create` + `initialize`; vira `try` se `initialize` pode falhar | [S] |
| `(T) create` | `-> T` | cria o objeto vazio (o `new` padrão) | [S] |
| `(T) name` | `-> str` | nome do tipo | [P] |
| `(T) supertypes` | `-> vec[type]` | | [P] |
| `(T) subtypes` | `-> vec[type]` | | [P] |
| `(T) attrs` | `-> vec[obj]` | igual a `&attrs` (`#name`, `#type`, `#labels`) | [S] |
| `(T) methods` | `-> vec[tag]` | seletores implementados | [P] |
| `(T) derives?` | `(T) derives? Init -> bool` | | [P] |
| `show T` | `[type "int"]` | representação de tipo | [S] |

Variáveis especiais de compilação **[S]**: `&attrs` (atributos do tipo), `&block` (bloco do método), `&it` (argumento implícito do bloco), `self`, `actor`.

---

## 5. Booleanos e lógica

| Seletor / op | Assinatura | |
|---|---|---|
| `and` `&&` | `bool and bool -> bool` (curto-circuito) | [S] |
| `or` `\|\|` | `bool or bool -> bool` (curto-circuito) | [S] |
| `not` | `not bool -> bool` | [S] |
| `?:` (Elvis) | `maybe[T] ?: T -> T` — usa o padrão só se o esquerdo é `nil` | [S] |
| `xor` | `(a) xor b -> bool` | [P] |
| `to_int` | `(true) to_int -> 1` | [P] |

---

## 6. Números

### 6.1 Operadores (`impl` em `Number`) **[S]**

| Op | Descrição | Obs |
|---|---|---|
| `+` `-` `*` | soma, subtração, multiplicação | |
| `/` | divisão real (float) | `10 / 0` → `Fail #zero_division` |
| `//` | divisão inteira | `try[int]` |
| `%` | resto | `10 % 0` → `Fail` |
| `**` | potência | direita-associativa |
| `==` `!=` `<` `>` `<=` `>=` | comparação | |
| `like` `~` | semelhança entre tipos numéricos (`5_000_000 long ~ int`) | |
| `-x` | negação (unário) | |
| `&&&` `\|\|\|` `^^^` `~~~` `<<<` `>>>` | bit a bit (E, OU, XOR, NÃO, desloc. esq./dir.) | só `Integer` |
| `..` / `to` / `by` | ranges (seção 9) | |
| `::T` | conversão numérica, `try[T]` (`42::long` não falha) | |

### 6.2 Mensagens de `Number`

| Seletor | Assinatura | |
|---|---|---|
| `abs` | `-> T` | [S] (usado em `tpk_f`) |
| `even?` `odd?` | `-> bool` | [S] / [P] |
| `zero?` `positive?` `negative?` | `-> bool` | [P] |
| `sign` | `-> int` (-1, 0, 1) | [P] |
| `succ` `pred` | `-> T` | [P] |
| `min: b` `max: b` | `-> T` | [P] |
| `clamp: low, high` | `-> T`, mantém dentro do intervalo | [P] |
| `between? low, high` | `-> bool` | [P] |
| `divmod: b` | `-> [q, r]` | [P] |
| `div: b` `mod: b` `rem: b` | `-> T` | [P] |
| `pow: e` / `pow: e, mod: m` | potência (modular) | [P] |
| `sqrt` | `-> double` (`Fail` se negativo) | [P] |
| `cbrt` | `-> double` | [P] |
| `gcd: b` `lcm: b` | `-> int` | [P] |
| `factorial` | `-> try[ulong]` | [P] |
| `prime?` | `-> bool` | [P] |
| `digits` | `-> vec[int]` | [P] |
| `bit_length` | `-> int` | [P] |
| `to_double` `to_float` `to_int` `to_long` | conversões que nunca falham | [P] |
| `to_str` | `-> str` | [P] |
| `to_str: base` | `(255) to_str: 16 -> "ff"` | [P] |
| `to_char` | `(65) to_char -> `A`` | [P] |
| `format: spec` | `(3.14159) format: ".2" -> "3.14"` (como `$x:.2`) | [S] (forma `$x:.2`) / [P] |
| `round` `floor` `ceil` `trunc` | `-> int` | [P] |
| `round: places` | `(3.14159) round: 2 -> 3.14` | [P] |
| `nan?` `infinite?` `finite?` | `-> bool` (floats) | [P] |
| `max_value` `min_value` | `(int) max_value` | [P] |

### 6.3 Laços numéricos **[S]**

| Seletor | Exemplo | |
|---|---|---|
| `times` | `(3) times do … end` | [S] |
| `upto` | `(1) upto 3 => \|i\| do … end` | [S] |
| `downto` | `(3) downto 1 => \|i\| do … end` | [S] |
| `step` | `(1) step: 10, every: 3 => \|i\| …` | [P] |
| `seconds` `minutes` `hours` `days` `millis` | `(30) seconds -> Duration` (usado em `(Time) after (30) seconds`) | [S]/[P] |

### 6.4 Constantes e módulo `Math` **[P]**

`(Math) pi`, `e`, `tau`, `infinity`, `nan`.
`(Math) sin: x`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2: y, x: x`, `sinh`, `cosh`, `tanh`, `exp`, `ln`, `log: x, base: b`, `log2`, `log10`, `sqrt`, `hypot: a, b: b`, `floor`, `ceil`, `round`, `abs`, `min`, `max`, `to_radians`, `to_degrees`.
`(Math) sum: vec`, `mean`, `median`, `variance`, `stddev` (estatística básica).

### 6.5 `Random` **[P]**

`(Random) int: low, high`, `(Random) double`, `(Random) bool`, `(Random) pick: vec`, `(Random) shuffle: vec`, `(Random) seed: n -> Random` (gerador determinístico), `(Random) bytes: n`, `(Random) uuid -> str`.

---

## 7. Controle de fluxo como mensagens

### 7.1 `bool` **[S]**

| Seletor | Exemplo | |
|---|---|---|
| `then ⟨bloco⟩` | `(cond) then { … }` — executa se verdadeiro | [S] |
| `otherwise ⟨bloco⟩` | `(cond) otherwise { … }` — executa se falso | [S] |
| `then … otherwise …` | duas partes, mesmo receptor; aceita `do … otherwise do … end` | [S] |
| `then a otherwise b` | forma como expressão: `(x > 0) then "pos" otherwise "neg"` | [S] (visto em Dijkstra) |
| `and_then ⟨bloco⟩` `or_else ⟨bloco⟩` | curto-circuito com bloco | [P] |

### 7.2 Estruturas **da linguagem** (não são mensagens) **[S]**

`if / elsif / else / end`, `if c yield a else b`, `unless`, `when … is … default`, `alternate … is (outbox) receive …`, `while`, `until`, `being … while|until`, `for x in coleção`, `for … yield … if …`, `loop`, `break`, `next`, `return`.

---

## 8. Strings, chars, regex

### 8.1 Literais e formatação **[S]**

`"texto $var $(expr) $price:.2"`, `\"cru\n"` (sem escape), `~"regex"`, `` `c` `` (char), `begin{TAG} … end{TAG}` (heredoc).

### 8.2 Operadores

| Op | Descrição | |
|---|---|---|
| `+` | concatenação | [S] |
| `*` | repetição (`"ab" * 3`) | [P] |
| `==` `!=` `<` `>` `<=` `>=` | comparação lexicográfica | [S]/[P] |
| `like` `~` | `"42" like 42` | [S] |
| `'nth` | `s'nth 0 -> char` · `s'nth 0..2 -> str` · `s'nth -1` | [P] |
| `'head` `'tail` | primeiro char · restante | [P] |
| `::T` | `"42"::int`, `"3.5"::double`, `"true"::bool` → `try[T]` | [S] |
| `<<` | acrescenta no fim | [P] |
| `\\` | remove ocorrências | [P] |

### 8.3 Mensagens de `str`

**Caixa e aparência** — `upcase` [P*], `downcase` [P*], `capitalize` [P*], `swapcase`, `title_case`, `trim`, `trim_left`, `trim_right`, `squeeze`, `center: width`, `pad_left: n, with: ch`, `pad_right: n, with: ch`, `reverse`, `repeat: n`.
(*) citados por você como parte da stdlib; todos devolvem nova `str`.

**Consulta**

| Seletor | Assinatura | |
|---|---|---|
| `length` `size` | `-> int` | [P] |
| `empty?` `blank?` | `-> bool` | [P] |
| `has? sub` | contém substring/char | [P] (por analogia a `vec`) |
| `starts_with? s` `ends_with? s` | `-> bool` | [P] |
| `index_of: s` `last_index_of: s` | `-> maybe[int]` | [P] |
| `count: s` | ocorrências | [P] |
| `match? re` | `(s) match? ~"^\d+$" -> bool` | [P] |
| `match re` | `-> maybe[obj]` (grupos) | [P] |
| `scan re` | `-> vec[str]` | [P] |
| `digit?` `alpha?` `alnum?` `space?` `upper?` `lower?` | `-> bool` | [P] |

**Transformação**

| Seletor | Assinatura | |
|---|---|---|
| `replace: a, with: b` | todas as ocorrências | [P] |
| `replace_first: a, with: b` | | [P] |
| `delete: s` | remove | [P] |
| `slice: from, to: to` | substring | [P] |
| `take: n` `drop: n` | | [P] |
| `split: sep` | `-> vec[str]` | [P] |
| `split_lines` / `lines` | `-> vec[str]` | [P] |
| `words` | `-> vec[str]` | [P] |
| `chars` | `-> vec[char]` | [P] |
| `bytes` | `-> vec[ubyte]` | [P] |
| `join` (em `vec[str]`) | ver seção 9 | [P] |
| `format: values` | `("$a e $b") format…` ou `("%s")`; preferir interpolação | [P] |
| `to_tag` | `"ok" -> #ok` | [P] |
| `to_sym` | sinônimo | [P] |
| `escape` / `unescape` | | [P] |
| `encode: enc` / `decode` | UTF-8, ASCII, Base64 (ver seção 17) | [P] |
| `compare: other` | `-> -1/0/1` | [P] |
| `casecmp: other` | comparação sem caixa | [P] |
| `hash` | | [P] |

### 8.4 `char` [P]

`code` (`-> int`), `upcase`, `downcase`, `digit?`, `alpha?`, `space?`, `to_str`, `to_int` (dígito), `succ`, `pred`.

### 8.5 `Regex` (`~"…"`) [P]

`(re) match: s -> maybe[obj]`, `match? s`, `scan: s`, `replace: s, with: r`, `split: s`, `source -> str`, `flags`. Grupos nomeados viram chaves do objeto devolvido.

---

## 9. Vetores (`vec[T]`) e Ranges

### 9.1 Criação **[S]**

`[1, 2, 3]`, `%[1 2 3]`, `(Vector) new: [1, 2]`, `1 to 6`, `1..5`, `(vec[int]) new`.

### 9.2 Acesso

| Seletor | Assinatura | |
|---|---|---|
| `element_at i` | `-> try[T]` | [S] |
| `first` `last` | `-> maybe[T]` | [S] |
| `first: n` `last: n` | `-> vec[T]` | [P] |
| `length` | `-> int`, O(1) | [S] |
| `v'nth i` · `v'nth a..b` · `v'nth -1` | | [S] |
| `v'head` · `v'tail` | primeiro · resto | [S] |
| `has? x` `has_all? a, b` `has_any? a, b` | `-> bool` | [S] |
| `empty?` `any?` `none?` | `-> bool` | [P] |
| `index_of: x` `last_index_of: x` | `-> maybe[int]` | [P] |
| `count` · `count: x` · `count ⟨bloco⟩` | | [P] |
| `sample` | elemento aleatório | [P] |

### 9.3 Construção (sempre devolve novo vetor)

| Seletor / op | Descrição | Custo | |
|---|---|---|---|
| `append x` · `v << x` | no fim | O(1)\* | [S] |
| `unshift x` · `x >> v` | no início | O(1)\* | [S] |
| `push a, b, …` | vários no fim | | [S] |
| `insert x at i` | em posição | O(log n) | [S] |
| `merge a, b, …` · `v + w` | concatenação | O(log n) | [S] |
| `pop` · `shift` | remove último · primeiro | O(1)\* | [S] |
| `delete x` · `v \\ x` | remove todas as ocorrências | O(n) | [S] |
| `delete_nth i` | remove por índice | O(log n) | [S] |
| `copy` | O(1) | | [S] |
| `copy_with {#i: x}` / `put: x at: i` | substitui elemento | | [P] |
| `compact` | remove `nil` | | [P] |
| `concat: vec` | | | [P] |

### 9.4 Iteração e transformação (Enumerable)

| Seletor | Assinatura | |
|---|---|---|
| `each ⟨bloco⟩` | executa para cada elemento | [S] |
| `each_with_index` | `\|x, i\|` | [P] |
| `collect ⟨bloco⟩` (`map`) | `-> vec[R]` | [S] / [P] |
| `select ⟨bloco⟩` (`filter`) | `-> vec[T]` | [S] / [P] |
| `reject ⟨bloco⟩` | `-> vec[T]` | [S] |
| `reduce: init with ⟨bloco⟩` (`fold`, `inject`) | acumula | [P] |
| `sum` · `product` | | [P] |
| `min` · `max` · `min_by ⟨bloco⟩` · `max_by` | `-> maybe[T]` | [P] |
| `sort` · `sort_by ⟨bloco⟩` · `sort_with ⟨cmp⟩` | `-> vec[T]` | [P] |
| `reverse` | `-> vec[T]` | [S] |
| `uniq` | | [P] |
| `flatten` · `flat_map ⟨bloco⟩` | | [P] |
| `zip: other` | `-> vec[[A, B]]` | [P] |
| `unzip` | | [P] |
| `take: n` · `drop: n` · `take_while ⟨b⟩` · `drop_while ⟨b⟩` | | [P] |
| `slice: from, to: to` | | [P] |
| `find ⟨b⟩` (`detect`) · `find_index ⟨b⟩` | `-> maybe[T]` | [P] |
| `all? ⟨b⟩` · `any? ⟨b⟩` · `none? ⟨b⟩` · `one? ⟨b⟩` | `-> bool` | [P] |
| `group_by ⟨b⟩` | `-> obj[vec[T]]` | [P] |
| `partition ⟨b⟩` | `-> [vec, vec]` | [P] |
| `tally` | `-> obj[int]` (contagem) | [P] |
| `each_slice: n` · `each_cons: n` | | [P] |
| `chunk_while ⟨b⟩` | | [P] |
| `rotate: n` | | [P] |
| `shuffle` | | [P] |
| `cycle` | `-> Iterator` infinito | [P] |
| `combinations: k` · `permutations` · `product: other` | | [P] |
| `join: sep` | `vec[str] -> str` | [P] |
| `to_obj` | `vec[[tag, V]] -> obj[V]` | [P] |
| `to_set` | | [P] |
| `intersect: o` · `union: o` · `difference: o` | operações de conjunto | [P] |
| `sum_by ⟨b⟩` · `average` | | [P] |
| `binary_search: x` | em vetor ordenado, `try[int]` | [P] |

Cada bloco aceita `&it`: `(numbers) select { (&it) even? }` **[S]**.

### 9.5 `Range` (`1..5`, `1 to 6`, `1 to 10 by 2`) **[S]/[P]**

Um range *é* um vetor numérico (spec), mas pode ser preguiçoso: `first`, `last`, `length`, `has? x`, `each`, `reverse`, `step: n`, `sum`, `to_vec`, `empty?`, `min`, `max`. Todas as mensagens de `vec` valem. `5 to 1 by -1` conta para baixo (limite final exclusivo) **[S]**.

---

## 10. Objetos (`obj[T]`) — mapas de tags

### 10.1 Criação **[S]**

`{#id: 1}`, `#{id: 1}`, `%{id: 1 name: "x"}`, `#{}`.

### 10.2 Acesso e consulta

| Seletor | Assinatura | |
|---|---|---|
| `get #k` | `-> try[T]` (estrito) | [S] |
| `maybe_get #k` | `-> maybe[T]` | [S] |
| `obj's #k` | seguro, `nil` se não existir | [S] |
| `obj's [#a, #b]` | caminho aninhado | [S] |
| `get_element #k` | `-> {#k: v}` | [S] |
| `element_at i` | `-> {#k: v}` | [S] |
| `obj'nth i` · `'nth a..b` · `'head` · `'tail` | | [S] |
| `has_key? #k` | | [S] |
| `has_value? v` | | [S] |
| `has? #k: v` | par existe | [S] |
| `length` · `empty?` | | [P] |
| `keys` · `values` · `pairs` | `vec[tag]`, `vec[V]`, `vec[[tag, V]]` | [P] |
| `fetch: #k, default: v` | | [P] |
| `dig: [#a, #b]` | | [P] |
| `key_of: v` | `maybe[tag]` | [P] |

### 10.3 Construção (imutável)

| Seletor / op | |
|---|---|
| `copy` | O(1) [S] |
| `copy_with {#k: v}` · `obj \| {#k: v}` | cópia com alteração [S] |
| `obj << {#k: v}` | acrescenta [S] |
| `obj \\ #k` (aceita lista) | remove [S] |
| `delete_key #k` | remove [P] |
| `merge: other` · `deep_merge: other` | [P] |
| `update #k with ⟨bloco⟩` | [P] |
| `select ⟨bloco⟩` · `reject ⟨bloco⟩` · `collect ⟨bloco⟩` | `\|k, v\|` [P] |
| `map_values ⟨bloco⟩` · `map_keys ⟨bloco⟩` | [P] |
| `filter_keys: […]` · `except: […]` · `slice: […]` | [P] |
| `invert` | [P] |
| `to_vec` | [P] |
| `each ⟨bloco⟩` | `\|k, v\|` [S] |
| `reduce`, `all?`, `any?`, `count`, `find`, `min_by`, `max_by`, `sort_by`, `group_by` | como em `vec`, com `\|k, v\|` [P] |

### 10.3.1 Comprehension **[S]**

`for name, age in users yield (name: age) if age >= 18`. O `(k: v)` é par de chave dinâmica.

---

## 11. Erros: `Fail`, `try`, `maybe`

| Seletor | Assinatura | |
|---|---|---|
| `(Fail) raise msg` | `-> fail` | [S] |
| `(Fail) raise #tag, msg` | falha classificada | [S] |
| `(f) fail?` · `(x) ok?` | `-> bool` | [S] |
| `(f) kind` | `-> tag` | [S] |
| `(f) message` | `-> str` | [P] |
| `(f) cause` | `-> maybe[fail]` | [P] |
| `(f) trace` | pilha de chamadas | [P] |
| `(f) wrap: msg` | encadeia causa | [P] |
| `expr rescue \|f\| { … }` | troca falha por valor; senão devolve o próprio | [S] |
| `(x) unwrap` | `try[T] -> T` ou encerra com `Erro: msg` | [P] |
| `(x) unwrap_or: v` | | [P] |
| `(x) or_else ⟨bloco⟩` | | [P] |
| `(x) map ⟨bloco⟩` | aplica só se for `ok` | [P] |
| `(x) and_then ⟨bloco⟩` | encadeia `try` | [P] |
| `(x) maybe_value` | `try[T] -> maybe[T]` | [P] |
| `x ?: padrão` | trata `nil` | [S] |
| `(x) to_maybe` · `(x) to_try: failure` | | [P] |

**Regras da linguagem [S]:** `try[T]` onde `T` é esperado, sem `rescue` ou `fail?`, é erro de compilação. Fail ignorado que chega ao fim do programa imprime `Erro: <mensagem>` e sai com código 1.

### Tags de falha padronizadas (catálogo)

| Tag | Quando |
|---|---|
| `#not_understood` | nenhum método entende a mensagem [S] |
| `#no_applicable` | existe o seletor, mas não para esses tipos [S] |
| `#not_found` | busca sem resultado [S] |
| `#range` | valor fora do intervalo [S] |
| `#negative` `#too_old` `#denied` `#locked` | exemplos de domínio da spec [S] |
| `#parse` | conversão `::T` falhou [P] |
| `#zero_division` | `/ 0`, `// 0`, `% 0` [P] |
| `#index` | índice fora do vetor [P] |
| `#key` | chave ausente em `get` [P] |
| `#type` | tipo incompatível em tempo de execução [P] |
| `#closed` | envio em canal fechado [S]\* |
| `#timeout` | tempo esgotado [P] |
| `#io` | erro de arquivo/rede [P] |
| `#invalid` | argumento inválido [P] |
| `#deadlock` | todas as tasks esperando [S]\* |

(\*) comportamento descrito na spec, sem tag nomeada.

---

## 12. Lambdas, blocos e funções

| Seletor | Assinatura | |
|---|---|---|
| `result args` | executa o lambda: `(f) result 5, 2` | [S] |
| `(&block) result x` | executa o bloco anônimo do método | [S] |
| `arity` | `-> int` | [P] |
| `curry` | `-> lambda` | [P] (spec implementa `curry:` em código de usuário) |
| `compose: g` | `(f) compose: g` ⇒ `f ∘ g` | [P] |
| `then_do: g` | `(f) then_do: g` ⇒ `g ∘ f` | [P] |
| `partial: a, b` | aplicação parcial | [P] |
| `memoize` | `-> lambda` com cache | [P] |
| `flip` | troca a ordem de 2 argumentos | [P] |
| `identity` | `(Fn) identity` | [P] |
| `constant: v` | `(Fn) constant: 7` | [P] |
| `lazy` | transforma em geração preguiçosa | [P] |

`return` em `do … end` sai do **método**; em lambda `|x| { }` sai só do lambda **[S]**.

---

## 13. Records, derivações e labels

### 13.1 Derivações **[S]**

| Derivação | Gera |
|---|---|
| `Init` | `initialize` automático, na ordem dos campos |
| `Repr` | `stringify` automático (`Person{name: "Ana"}`) |
| `Ord` | `<` `>` `<=` `>=` e `compare` |
| `Eq` | `==` e `!=` estruturais |
| `Iter` | iteração (`for`, `each`) |
| `Block` | `(obj) value` |
| `Vec` | `to_vec` dos atributos |
| `Proxy` | `forward` para o atributo `proxied` + `intercept` |
| `Prop` | habilita os labels abaixo |

Derivações adicionais **[P]**: `Hash` (`hash`), `Copy` (`copy_with` por campo), `Default` (`(T) default`), `Json` (`to_json`/`from_json`), `Enum` (valores nomeados), `Debug`.

### 13.2 Labels de propriedade (requer `Prop`) **[S]**

| Label | Mensagens geradas |
|---|---|
| `readable` | getter `(x) campo` |
| `configurable` | setter `(x) campo: v` |
| `accessible` | getter e setter |
| `proxied` | alvo do `Proxy` |
| labels livres (`depositable`) | marcam campos para `abstract`/`@for` |

### 13.3 Tipos auxiliares de composição **[S]**

`use T as nome`, `(nome) has args…` na construção.

---

## 14. Concorrência (CSP com inbox/outbox)

### 14.1 Tasks

| Seletor | Descrição | |
|---|---|---|
| `! msg` | envio assíncrono, devolve `task` | [S] |
| `? msg` | envia e espera o resultado | [S] |
| `? task` | espera uma task já iniciada → `try[T]` | [S] |
| `spawn ⟨bloco⟩` | cria task | [S] |
| `(task) done?` | terminou? | [P] |
| `(task) cancel` | cancela | [P] |
| `(task) wait: (Duration)` | espera com limite → `maybe[try[T]]` | [P] |
| `(Task) all: vec[task]` | espera todas | [P] |
| `(Task) any: vec[task]` | a primeira que terminar | [P] |
| `(Task) current` | task atual | [P] |
| `(Task) yield` | cede a vez | [P] |

### 14.2 Canais **[S]**

| Seletor | Assinatura | |
|---|---|---|
| `(IOBox[T]) new` / `new: n` | `-> [inbox[T], outbox[T]]` (capacidade 0 ou `n`) | [S] |
| `(inbox) send x` | `-> try[nil]` (Fail se fechado) | [S] |
| `(outbox) receive` | `-> maybe[T]` (`nil` = fechado e drenado) | [S] |
| `(outbox) each ⟨bloco⟩` | consome até fechar | [S] |
| `(inbox) copy` | outra inbox para o mesmo canal | [S] |
| `(inbox) close` | fecha para todas as cópias | [S] |
| `(inbox) closed?` · `(outbox) closed?` | | [P] |
| `(inbox) try_send x` | não bloqueia, `bool` | [P] |
| `(outbox) try_receive` | `maybe[T]` sem bloquear | [P] |
| `(outbox) receive_within: (Duration)` | com timeout | [P] |
| `(outbox) size` · `capacity` | | [P] |
| `(outbox) drain` | `-> vec[T]` | [P] |
| `(outbox) to_vec` | consome tudo | [P] |
| `(outbox) collect` · `select` · `reject` | pipelines sobre canal | [P] |

`alternate … is (outbox) receive x => …`, `is (outbox) closed =>`, `default =>` **[S]**.

### 14.3 Tempo e sincronização

| Seletor | |
|---|---|
| `(Time) after (30) seconds -> outbox[Instant]` | [S] |
| `(Time) every: (Duration) -> outbox[Instant]` | [P] |
| `(Time) sleep: (Duration)` | [P] |
| `(Time) timeout: (Duration) do … end -> try[T]` | [P] |
| `Mutex`, `Semaphore`, `WaitGroup` | **não incluir**: o modelo é CSP; use canais [decisão] |
| `(Atomic[T]) new: v` | contador atômico, se desejado [P] |

### 14.4 Atores **[S]**

`behavior T … end`, `actor` no lugar de `self`, envio só com `!`. Padrões de apoio **[P]**: `(Actor) tell: msg`, `(Actor) ask: msg -> try[any]`, `(Actor) stop`, supervisão (`(Supervisor) one_for_one: […]`), `(Registry)` de nomes.

---

## 15. I/O, sistema e ambiente **[P]**

### 15.1 Console
`show` · `read` · `print` · `eprint` · `(Console) clear` · `(Console) color: #red text: "x"` · `(Console) width`.

### 15.2 Arquivos (`File`, `Path`, `Dir`)
`(File) read: path -> try[str]` · `(File) read_lines: path` · `(File) write: path, text: s` · `(File) append: path, text: s` · `(File) exists?: path` · `(File) delete: path` · `(File) copy: a, to: b` · `(File) move: a, to: b` · `(File) size: path` · `(File) each_line: path => |l| …` · `(File) open: path, mode: #read => |f| …` (fecha sozinho).
`(Path) join: a, b` · `base` · `dir` · `ext` · `absolute` · `exists?` · `file?` · `dir?`.
`(Dir) list: path` · `create: path` · `delete: path` · `glob: pattern` · `cwd` · `home` · `tmp`.

### 15.3 Processo e ambiente
`(Env) get: "HOME" -> maybe[str]` · `(Env) set` · `(Env) all`.
`(Process) args -> vec[str]` · `pid` · `exit: code` · `run: cmd, args: [..] -> try[obj]` (`#stdout`, `#stderr`, `#code`) · `cwd`.
`(System) os` · `arch` · `cpus` · `hostname` · `version`.

### 15.4 Rede (módulo opcional)
`(Http) get: url -> try[obj]` · `post: url, body: b` · `(Url) parse: s` · `(Socket) …`.

---

## 16. Tempo e datas **[P]**

| Tipo | Mensagens |
|---|---|
| `Duration` | `(30) seconds` · `minutes` · `hours` · `days` · `millis` · `+` · `-` · `*` · `as_seconds` · `as_millis` · `to_str` |
| `Instant` (ponto no tempo, monotônico) | `(Time) now` · `(i) elapsed -> Duration` · `-` entre instantes · `after: d` |
| `Date` | `(Date) today` · `new: y, m, d` · `year` `month` `day` `weekday` · `add_days: n` · `add_months: n` · `format: "%Y-%m-%d"` · `parse: s -> try[Date]` · `leap?` · `<`/`==` · `days_until: d` |
| `DateTime` | `(DateTime) now` · `utc` · `zone: "America/Rio_Branco"` · `to_unix` · `format` · `parse` · `hour` `minute` `second` |

---

## 17. Dados, formatos e utilitários **[P]**

| Módulo | Mensagens |
|---|---|
| `Json` | `(Json) parse: s -> try[obj[any]]` · `(Json) generate: x -> str` · `(Json) pretty: x` |
| `Csv` | `parse: s, header: true` · `generate` |
| `Toml` / `Yaml` | `parse` · `generate` |
| `Base64` | `encode: s` · `decode: s -> try[str]` |
| `Hex` | `encode` · `decode` |
| `Hash` | `(Hash) sha256: s` · `md5` · `sha1` |
| `Uuid` | `(Uuid) v4` · `parse` |
| `Url` | `encode: s` · `decode: s` |
| `Text` | `(Text) wrap: s, width: n` · `indent` · `dedent` · `levenshtein: a, b` |
| `Number` | `(Number) parse: s -> try[Number]` · `format_thousands` |
| `Locale` | formatação de moeda e data |
| `Color` | `rgb`, `hex`, `hsl` (útil para jogos/prototipação) |
| `Vec2` `Vec3` `Matrix` | vetores matemáticos (cuja base já aparece como exemplo de `op +`) |
| `Bits` | `(Bits) count: n` · `reverse` · `parity` |

---

## 18. Coleções adicionais **[P]**

Todas imutáveis e persistentes, com as mesmas mensagens de Enumerable da seção 9.4.

| Tipo | Papel | Mensagens principais |
|---|---|---|
| `Set[T]` | conjunto (HAMT) | `add x` · `delete x` · `has? x` · `union` · `intersect` · `difference` · `subset?` · `length` · `to_vec` |
| `Dictionary[K, V]` | mapa com chaves de qualquer tipo | `get: k -> try[V]` · `maybe_get: k` · `put: k, v` · `delete: k` · `has_key? k` · `keys` · `values` · `pairs` · `merge` |
| `Deque[T]` | fila dupla | `push_front` · `push_back` · `pop_front` · `pop_back` · `front` · `back` |
| `Queue[T]` | FIFO | `enqueue x` · `dequeue -> [x, queue]` · `peek` |
| `Stack[T]` | LIFO | `push x` · `pop -> [x, stack]` · `peek` |
| `PriorityQueue[T]` | heap persistente | `insert x with: prio` · `pop_min` · `peek` |
| `SortedMap[K, V]` / `SortedSet[T]` | árvore ordenada | `first` · `last` · `range: a to b` |
| `Pair[A, B]` / `Tuple` | par | `first` · `second` · `swap` |
| `Graph[N, W]` | grafo ponderado (generaliza o exemplo de Dijkstra) | `add_edge` · `neighbors` · `shortest_path: a, to: b` |
| `Matrix` | | `rows` · `cols` · `*` · `transpose` · `det` |
| `Bag[T]` | multiconjunto | `add` · `count: x` |
| `Iterator[T]` | geração preguiçosa | `next` · `take: n` · `lazy` · `collect` |
| `Ring[T]` | buffer circular | |

---

## 19. Reflexão, depuração, testes **[P]**

| Módulo | Mensagens |
|---|---|
| `Reflect` | `(Reflect) methods: T` · `attrs: T` · `supertypes: T` · `responds_to: x, tag` · `send: x, tag, args` |
| `Debug` | `(Debug) trace` · `(Debug) dump: x` · `(Debug) assert: cond, msg: s` · `(Debug) time ⟨bloco⟩ -> Duration` |
| `Log` | `(Log) info: s` · `warn:` · `error:` · `debug:` · `level: #warn` · `to: outbox` |
| `Test` | `(Test) suite: "nome" => do … end` · `test: "descrição" => do … end` · `expect: a, to_be: b` · `expect_fail: expr, kind: #tag` · `run` |
| `Bench` | `(Bench) run: "nome", times: n => do … end` |
| `Doctest` | executa os exemplos `; -> valor` dos `@doc` como testes (ver seção 22) |
| `Compile` | utilidades de tempo de compilação: `(Compile) type_of: x`, `(Compile) error: msg` |

---

## 20. Operadores, tabela-resumo (todos são mensagens `op`)

| Operador | Seletor | Tipos principais | |
|---|---|---|---|
| `+ - * / // % **` | aritmética | Number | [S] |
| `+` | concatenar | str, vec | [S] |
| `* ` | repetir | str, vec | [P] |
| `== != < > <= >=` | comparação | todos / Comparable | [S] |
| `like` `~` | semelhança | todos | [S] |
| `and or not && \|\|` | lógicos | bool | [S] |
| `&&& \|\|\| ^^^ ~~~ <<< >>>` | bit a bit | Integer | [S] |
| `<< >> \| \\` | coleções: acrescentar fim/início, copiar-com, remover | vec, obj, str | [S] |
| `..` `to` `by` | ranges | Number, char | [S] |
| `?:` | Elvis | maybe | [S] |
| `'s` | acesso seguro | obj, record | [S] |
| `'nth` `'head` `'tail` | posição | vec, obj, str | [S] |
| `::T` | conversão | todos | [S] |
| `isa` `&` | tipo / subtipo | todos | [S] |
| `rescue` | tratamento | try | [S] |
| `!` `?` | async / await | | [S] |

---

## 21. Organização em módulos (sugestão de arquivos)

```
stdlib/
├── prelude.ma          show read print exit sleep assert, Object, bool, then/otherwise      [carga automática]
├── types.ma            hierarquia (Number, Integer, Collection…), type, derivações
├── number.ma           Number, int/float, Math, Random
├── text.ma             str, char, Regex, Text, formatação
├── collections.ma      vec, Range, obj, Set, Dictionary, Deque, Queue, Stack, Pair
├── functional.ma       lambda, curry, compose, memoize, Iterator
├── errors.ma           Fail, try, maybe, catálogo de tags
├── reflect.ma          message, Reflect, OMOP (forward/intercept/target/Proxy)
├── concurrency.ma      Task, IOBox, Time (after/every), Actor, Supervisor
├── time.ma             Duration, Instant, Date, DateTime
├── io.ma               Console, File, Path, Dir, Env, Process, System
├── data.ma             Json, Csv, Toml, Yaml, Base64, Hash, Uuid
├── net.ma              Http, Url, Socket                    [opcional]
└── dev.ma              Debug, Log, Test, Bench, Doctest
```

**Carga:** `prelude` + `types` + `number` + `text` + `collections` + `functional` + `errors` sempre; `reflect`, `concurrency`, `time`, `io`, `data` por `use`; `net` e `dev` opcionais.

---

## 22. Decisões abertas (precisam de você)

1. **`show` / `read` / `spawn` / `loop` como palavras reservadas ou mensagens da prelude?** A gramática atual (EBNF) os trata como `PrefixCommand`.
2. **Sintaxe de primitiva nativa.** Sugestão: `on upcase -> str = native: #str_upcase`.
3. **`map`/`filter`/`reduce` vs `collect`/`select`/`reject`.** A spec usa os nomes Smalltalk/Ruby (`collect`, `select`, `reject`). Mantenho-os como principais e `map`/`filter` como aliases?
4. **Duração.** `(Time) after (30) seconds` exige `(30) seconds` → `Duration`. Aprova essa regra?
5. **Palavras reservadas como seletores.** `to`, `by`, `and`, `or`, `not`, `like`, `obj` não podem ser partes de mensagem; por isso `step: n, by: m` usaria `by`, que é reservada. Opções: renomear (`(1) step: 10, every: 3`) ou liberar `by` como parte quando seguida de `:`. Proposta acima: **`every:`**.
6. **`read`** coincide com `on read` do `HDD`/`SSD` da spec: despacho múltiplo resolve (o do usuário tem receptor explícito), mas vale confirmar.
7. **Strings.** `str` indexado por caractere Unicode (grafemas)? `length` conta *code points* ou *bytes*? Proposta: `length` em caracteres, `bytes length` em bytes.
8. **`Set` e `Dictionary`.** Entram no *core* ou ficam como extras?
9. **Doctests.** Aceitar `; -> valor` como teste executável em qualquer arquivo `.mach`?
10. **Rede, JSON e Test na stdlib padrão ou como pacotes à parte?**

---

## 23. Checklist de implementação por tipo (modelo para cada página de referência)

Cada mensagem da stdlib deve ter, no `@doc` do `.mach` correspondente:

```
@doc
    seletor: assinatura (tipos de entrada → saída)
    Descrição de uma frase.
    Falhas: #tag quando …
    Custo: O(…)
    Exemplos:
        ("ana") upcase     ; -> "ANA"
        ("") upcase        ; -> ""
@end
```

Assim a referência em Markdown e os testes de conformidade são **gerados** da própria stdlib.
