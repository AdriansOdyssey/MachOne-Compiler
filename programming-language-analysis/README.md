# MachOne JVM — 0.3.0-dev

Compilador MachOne com tipagem estática e geração direta de bytecode JVM. A implementação está em desenvolvimento; veja [o estado real](docs/implementation-status.md), [a arquitetura](docs/architecture.md), [a matriz da linguagem](docs/conformance-matrix.md) e [o checklist da stdlib](docs/stdlib-conformance.md).

Esta etapa amplia objetos e mensagens: composição `use ... as`, varargs, comprehensions, derivações, `perform/forward/intercept/proceed/redirect/Proxy` e biblioteca padrão com contratos estáticos. `show`, `read`, `then`, `otherwise`, `each`, `upto` e `downto` são mensagens. A biblioteca portátil `Pair/Queue/Stack` é compilada junto ao programa. RRB/CHAMP e concorrência foram adiados; isso não é anunciado como a linguagem ou a stdlib integralmente concluídas.

## Compilar e executar

Instale um **JDK completo 21 ou superior**, com `java` e `javac`. O wrapper Gradle 9.1.0 está incluído; não é necessário instalar Gradle separadamente.

```sh
chmod +x gradlew mach
./gradlew :tests:test :cli:installDist
./mach run examples/hello.mach
./mach run examples/messages_stdlib.mach
```

Depois da instalação, para executar seu próprio arquivo:

```sh
./mach run caminho/do/programa.mach
```

O caminho deve apontar para um arquivo que existe. `main.mach` e `seu_programa.mach` são nomes de exemplo, não arquivos criados automaticamente.

```sh
./mach --version
./mach help
./mach check examples/messages_stdlib.mach
./mach build examples/messages_stdlib.mach
java -Xverify:all -jar examples/messages_stdlib.jar
./mach test
```

O JAR gerado inclui o runtime e pode executar sem Gradle, ANTLR, ASM ou o compilador no classpath. O build usa ANTLR4 4.13.2, ASM 9.7.1 e JUnit 5.11.4; classes e APIs são limitadas ao Java 21. Os resultados efetivamente executados estão em [validation.md](docs/validation.md).

No Fedora, `javac` vem no pacote de desenvolvimento, por exemplo `java-25-openjdk-devel`. Se aparecer `Unsupported class file major version 69`, confirme com `./gradlew --version` que o checkout usa Gradle 9.1.0 e confira `java -version`, `javac -version` e `JAVA_HOME`.

No ambiente gerenciado, `./tools/cloud-build.sh` prepara ferramentas e testes; consulte [environment.md](docs/environment.md). As especificações em `specification/MachOne` e `specification/stdlib` permanecem preservadas. Elas contêm fragmentos dependentes e exemplos negativos: executar todo arquivo indiscriminadamente não substitui uma suíte de conformidade.
