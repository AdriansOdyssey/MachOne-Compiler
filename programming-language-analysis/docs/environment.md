# Ambiente de desenvolvimento

O wrapper usa Gradle 9.1.0, compatível com a execução em Java 25. O build usa o JDK atual e aplica `--release 21`, sem exigir um segundo JDK 21 quando o usuário já possui o JDK 25. Tanto o compilador quanto o runtime e os programas MachOne continuam produzindo bytecode Java 21.

O helper do ambiente de nuvem usa JDK Temurin 21.0.9+10 e Gradle 9.1.0, obtidos de fontes oficiais com verificação SHA-256. O runtime Java originalmente instalado não continha javac. Arquivos de ferramentas ficam fora do checkout, em `/workspace/toolchains`.

```sh
./tools/cloud-build.sh
export JAVA_HOME=/workspace/toolchains/jdk-21.0.9+10
export PATH="$JAVA_HOME/bin:$PATH"
./mach run examples/hello.mach
```

O script reutiliza os arquivos verificados e usa o proxy herdado. A verificação TLS continua habilitada e o Java usa o truststore do sistema quando disponível. Não há credenciais salvas no projeto. O script é específico para Linux x86_64; outros sistemas devem instalar um JDK completo (21 ou 25 foram validados) e usar o wrapper.

Em tarefas futuras, utilize o checkout existente: a tarefa já é isolada e não precisa de worktree adicional. Processos do onboarding não são pré-requisitos persistentes. O compilador/CLI não exige servidor ou banco de dados.

O wrapper fixa SHA-256 da distribuição Gradle. Para usá-lo nesta máquina com rede restrita, configure as propriedades HTTPS do Java a partir do proxy já fornecido, preservando a CA e as demais opções. O helper cloud-build realiza essa configuração sem desabilitar TLS.

Os fontes são versionados em `AdriansOdyssey/codex_teste`. A publicação do ambiente de nuvem e a validação de restauração em uma tarefa nova são operações distintas do envio de código ao GitHub.
