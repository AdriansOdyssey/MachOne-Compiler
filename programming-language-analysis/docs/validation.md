# Validação — MachOne 0.3.0-dev

Executada em 3 de novembro de 2026, no checkout final recuperado, Linux x86_64 e Gradle 9.1.0. Não se reutilizaram resultados do ambiente anterior. Todos os testes foram recompilados e executados; o JDK 25 usou build limpo. Compilação com `--release 21`.

| JDK | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| Temurin 21.0.9+10 | 443 | 0 | 0 | 0 |
| Temurin 25.0.1+8 | 443 | 0 | 0 | 0 |

`check`, `run` e `build` de `examples/messages_stdlib.mach` passaram nos dois JDKs. O JAR gerado com o JDK 25 executou no Java 21 com `-Xverify:all` e produziu exatamente a mesma saída que a execução direta. As 51 classes incluídas no artefato têm major version 65. O programa exercita composição, propriedades importadas, mensagens dinâmicas, varargs, comprehensions, blocos, controle por mensagens e o Pair da biblioteca portátil.
Os exemplos `oo_errors.mach` e `abstract_labels.mach` continuaram executando; o original `01_basic.mach` executou integralmente com entrada `teste`. Os 20 checksums originais passaram. A referência stdlib coincide byte a byte com o upload: SHA-256 `3a0e904206be68f65932ed383f9ae63dbb562b6a8681cd7621ba444789742c9d`.

Comandos reproduzíveis, com um JDK completo 21 ou superior:

```sh
./gradlew --no-daemon --max-workers=2 clean :tests:test :cli:installDist
./mach check examples/messages_stdlib.mach
./mach run examples/messages_stdlib.mach
./mach build examples/messages_stdlib.mach
java -Xverify:all -jar examples/messages_stdlib.jar
(cd specification && sha256sum -c SHA256SUMS)
```

No ambiente gerenciado, `tools/cloud-build.sh` instala e verifica os arquivos oficiais e configura o proxy. A validação usou esse Gradle instalado; `--offline` foi usado somente após baixar as dependências. O JDK 25 foi verificado contra o checksum oficial `8daf77d1aacffe38c9889689bc224a13557de77559d9a5bb91991e6a298baa0d`.

A suíte valida os casos exercitados, incluindo programas inválidos, tipagem estática e execução JVM real. Ela não demonstra conformidade integral de todas as propostas da stdlib ou dos 19 documentos. Pendências e recursos adiados constam de `implementation-status.md` e `stdlib-conformance.md`.

## Suítes da entrega 0.3

| Suíte | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| AnonymousBlocksTest | 9 | 0 | 0 | 0 |
| CollectionsLibraryTest | 24 | 0 | 0 | 0 |
| CompileTimeExpansionTest | 15 | 0 | 0 | 0 |
| CompilerTest | 73 | 0 | 0 | 0 |
| CompositionDerivationTest | 26 | 0 | 0 | 0 |
| ComprehensionTest | 8 | 0 | 0 | 0 |
| CoreLibraryTest | 27 | 0 | 0 | 0 |
| DivisionSafetyTest | 5 | 0 | 0 | 0 |
| ErrorSemanticsTest | 12 | 0 | 0 | 0 |
| ExtraCollectionsTest | 16 | 0 | 0 | 0 |
| IntegerWrapTest | 6 | 0 | 0 | 0 |
| LanguageIntegrationTest | 11 | 0 | 0 | 0 |
| LibraryIntegrationTest | 12 | 0 | 0 | 0 |
| MessageProtocolTest | 25 | 0 | 0 | 0 |
| MessageSyntaxTest | 12 | 0 | 0 | 0 |
| ModulesLibraryTest | 12 | 0 | 0 | 0 |
| NonLocalBlocksTest | 9 | 0 | 0 | 0 |
| ObjectAndBlockContractsTest | 20 | 0 | 0 | 0 |
| ObjectIntegrationTest | 12 | 0 | 0 | 0 |
| ObjectModelTest | 28 | 0 | 0 | 0 |
| ParserBoundaryTest | 8 | 0 | 0 | 0 |
| PrimitiveTypesTest | 15 | 0 | 0 | 0 |
| QuotedCollectionSyntaxTest | 8 | 0 | 0 | 0 |
| StandardLibraryTest | 16 | 0 | 0 | 0 |
| TypeSystemTest | 10 | 0 | 0 | 0 |
| UtilityLibraryTest | 10 | 0 | 0 | 0 |
| VarargsTest | 9 | 0 | 0 | 0 |
| frontend.LiteralDecoderTest | 5 | 0 | 0 | 0 |

---

# Histórico de validação — MachOne 0.2.0-dev

Executada em 2 de novembro de 2026, Linux x86_64, Gradle 9.1.0. JDKs utilizados: Temurin 21.0.9+10 e 25.0.1+8. Todas as classes do projeto são compiladas com --release 21.

| JDK | Testes executados | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| 21 | 253 | 0 | 0 | 0 |
| 25 | 253 | 0 | 0 | 0 |

Os testes do JDK 21 foram executados em build limpo. No JDK 25, a integração completa também foi regenerada e testada; as correções finais de semântica foram seguidas pela suíte inteira. Não são contagens de comandos simulados ou testes apenas registrados.

## Comandos e resultados

* `./gradlew --no-daemon --max-workers=2 --offline clean :tests:test :cli:installDist` — passou no JDK 21.
* `./gradlew --no-daemon --max-workers=2 --offline :tests:test :cli:installDist` — passou no JDK 25 após as alterações finais.
* `./mach run examples/oo_errors.mach` — executou abstracts, hierarquia, maybe/try, rescue e quatro variantes de envio com/sem dois-pontos.
* `./mach run examples/abstract_labels.mach` — compilou o abstract Deposit, gerou as mensagens a partir de labels e imprimiu 150.0.
* `./mach run specification/MachOne/01_basic.ma` — executou integralmente com entrada teste.
* JAR de oo_errors construído no JDK 25 e executado no Java 21 com `-Xverify:all` — passou.
* Teste de artefato executável verifica major version 65 para todas as classes incluídas e executa em processo JVM separado, sem classpath do compilador.
* Os 20 checksums SHA-256 da especificação original foram novamente verificados, sem alterações.

O modo offline foi usado porque as dependências já tinham sido baixadas neste ambiente. Uma primeira instalação normalmente precisa de acesso aos repositórios de dependências. TLS e checksums permaneceram habilitados.

## Suítes da execução final no JDK 21

| Suíte | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| AnonymousBlocksTest | 9 | 0 | 0 | 0 |
| CompileTimeExpansionTest | 15 | 0 | 0 | 0 |
| CompilerTest | 73 | 0 | 0 | 0 |
| ErrorSemanticsTest | 12 | 0 | 0 | 0 |
| IntegerWrapTest | 6 | 0 | 0 | 0 |
| LanguageIntegrationTest | 11 | 0 | 0 | 0 |
| MessageSyntaxTest | 12 | 0 | 0 | 0 |
| NonLocalBlocksTest | 9 | 0 | 0 | 0 |
| ObjectAndBlockContractsTest | 20 | 0 | 0 | 0 |
| ObjectIntegrationTest | 12 | 0 | 0 | 0 |
| ObjectModelTest | 28 | 0 | 0 | 0 |
| PrimitiveTypesTest | 15 | 0 | 0 | 0 |
| StandardLibraryTest | 16 | 0 | 0 | 0 |
| TypeSystemTest | 10 | 0 | 0 | 0 |
| frontend.LiteralDecoderTest | 5 | 0 | 0 | 0 |

A suíte valida os contratos implementados e suas integrações. RRB/CHAMP, concorrência/ownership, todos os quatro pilares de mensagens e a linguagem integral ainda não foram validados porque permanecem incompletos; veja implementation-status.md.
