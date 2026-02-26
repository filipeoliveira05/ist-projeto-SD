# BlockchainIST

Este documento descreve o projeto da cadeira de Sistemas Distribuídos 2025/2026.

## 1. Introdução

O objetivo do projeto de Sistemas Distribuídos (SD) é desenvolver uma _permissioned blockchain_, inspirada no desenho do Hyperledger Fabric (versão simplificada de ["Hyperledger fabric: a distributed operating system for permissioned blockchains", Androulaki _et al._](https://doi.org/10.1145/3190508.3190538)), sobre a qual será construída uma criptomoeda simples.
O sistema deverá suportar clientes que recebem comandos dos utilizadores e submetem esses pedidos a um dos múltiplos servidores de _blockchain_ replicados (_fabric peer_ na terminologia do Hyperledger Fabric), daqui em diante designados "nós". Para assegurarem coerência, os nós recorrem a um
serviço de ordenação centralizado (ou sequenciador), que
ordena totalmente as transações numa cadeia de blocos (a _blockchain_).

Ao contrário do Fabric real, será usado o modelo _order-execute_ (e não _execute-order-validate_):

- As transações são (depois de enviadas por um cliente a um nó) primeiro ordenadas pelo sequenciador;
- Depois são executadas localmente por cada nó;
- Não existem _endorsers_, _endorsements_, _readsets_ ou _writesets_.

O sistema é _**permissioned**_: as identidades dos processos (clientes, nós e sequenciador) que participam no sistema são conhecidas.

Sobre esta infraestrutura será implementada uma criptomoeda suportando as seguintes operações:

- Criar carteira;
- Remover carteira;
- Consultar saldo;
- Transferir valores entre carteiras;
- Consultar o estado da _blockchain_ (para depuração do sistema).

## 2. Modelo da Aplicação (Criptomoeda)

Existem uma ou mais organizações.
Por exemplo, as organizações podem ser bancos (privados ou estatais).
Cada organização é responsável por manter em execução um ou mais nós.

Existem também utilizadores, cada um pertencente a uma organização, na qual este confia.
Como explicamos de seguida, cada utilizador criar uma ou mais carteiras, através da qual pode
receber e trocar criptomoeda com carteiras de outros utilizadores.

Tanto as organizações, utilizadores e carteiras são univocamente identificadas por
_strings_ compostas por letras e/ou dígitos, sem espaços.

Existe um utilizador especial, chamado _central bank_ (BC), cuja carteira `bc` é pré-existente.
e tem inicialmente saldo de 1000 unidades de criptomeda.

> No Fabric, existe um serviço que gere a criação, remoção, etc. de utilizadores e organizações (_membership service provider_, ou MSP). A implementação do MSP está fora do âmbito do nosso projeto.

O estado global da aplicação consiste em:

- Um conjunto de carteiras existentes (incluindo a `bc`) e os utilizadores que as possuem;
- Um saldo associado a cada carteira, que nunca pode ser negativo.

### 2.1. Operações

As seguintes operações devem ser suportadas pela aplicação:

- **criaCarteira(idUtilizador, idCarteira)** - Cria uma nova carteira `idCarteira`,
  que fica associada ao utilizador `idUtilizador`.
  Deve falhar se já existir uma carteira com o mesmo identificador (`idCarteira`).

- **eliminaCarteira(idUtilizador, idCarteira)** - Remove a carteira existente (`idCarteira`), na posse do utilizador `idUtilizador`.
  Deve falhar se:
  - a carteira não existir ou se o saldo não for zero;
  - a carteira não pertencer ao utilizador.

- **leSaldo(idCarteira)** - Retorna o saldo atual da carteira.

- **transfere(idUtilizador, idCarteiraOrigem, idCarteiraDestino, quantia)** - Transfere da carteira `idCarteiraOrigem`, possuída pelo utilizador `idUtilizador`, para a carteira `idCarteiraDestino` uma `quantia` de unidades de criptomoeda. Deve falhar se:
  - alguma das carteiras não existir;
    - se a carteira de origem não pertencer ao utilizador;
  - o saldo da origem for insuficiente;
  - a quantia não for positiva.

- **leBlockchain** - Consulta a lista ordenada de transações que já foram entregues num dado nó.

Todas as operações, exceto **leSaldo** e **leBlockchain**, modificam o estado global e devem ser registadas na _blockchain_. A estas operações, designamos _transações_.

É esperado que o sistema replicado assegure linearizabilidade como critério de coerência.

> Nota: nos .proto e código inicial fornecido, usamos variantes inglesas as operações/parâmetros citadas acima (CreateWallet, etc.)

## 3. Arquitetura Geral

O sistema é composto por três tipos de processos: clientes, nós e sequenciador.

### 3.1. Clientes

Quando o cliente é lançado, este conhece os endereços de um ou mais nós.
Tipicamente, esses nós pertencem a uma organização na qual os utilizadores desse cliente confiam.

Cada cliente recebe comandos introduzidos por um ou mais utilizadores a partir da consola e envia cada pedido a um dos nós conhecidos pelo cliente (por exemplo, ao nó geograficamente mais próximo).

Assume-se que cada utilizador se autenticou perante o cliente que está a usar.
No caso de comandos que resultam em pedidos de transações, assume-se que o `utilizadorId` enviado na transação corresponde ao utilizador que, de facto, solicitou essa transação através da consola.

> Fica de fora do projeto:
>
> - Implementar os mecanismos de autenticação do utilizador no cliente.
> - Usar canais seguros que assegurem a autenticação de um nó conhecido perante o cliente.
>
> No entanto, estudaremos possíveis soluções nas aulas teóricas.

Os clientes são responsáveis por:

- Construir pedidos, que são enviados a um nó, usando gRPC;
- Apresentar as respostas ao utilizador;
- (Na Entrega 3) Assinar digitalmente os pedidos de transação.

### 3.2. _Fabric Peers_ - Nós

Cada nó pertence a uma organização.

Cada nó é simultaneamente:

- Servidor gRPC para os clientes;
- Cliente gRPC do sequenciador.

Cada nó mantém localmente:

- Uma cópia do estado global da aplicação (ver Secção 2);
- A _blockchain_ (cadeia de blocos recebidos do sequenciador).

Cada nó é responsável por:

- Receber blocos de transacções do sequenciador e adicioná-los à sua _blockchain_ local;
- Receber pedidos dos clientes;
- No caso de pedidos de transações: enviar as transações ao sequenciador e aguardar que estas sejam entregues num bloco;
- Responder aos clientes;
- (Na Entrega 3) Validar a integridade dos blocos de transações recebidos do serviço de ordenação.

> Fica de fora do projeto: usar canais seguros que assegurem a autenticação do sequenciador perante o nó.

### 3.3. Sequenciador

O sequenciador é um servidor central responsável por implementar difusão atómica baseada em blocos. As suas funções principais são:

- Receber transações individuais dos nós;
- Agrupar transações ordenadas em blocos;
- Atribuir ordem total aos blocos;
- Difundir os blocos por todos os nós. A difusão não será feita explicitamente pelo sequenciador mas sim pelos nós que pedirão ciclicamente ao sequenciador o próximo bloco de transacções que este complete.
- (Na Entrega 3) Assinar os blocos.

#### 3.3.1. Política de criação de blocos

Um bloco deve ser fechado quando ocorrer uma das seguintes condições:

- O número máximo de transações por bloco (N) for atingido;
- Um temporizador de valor T expirar desde a criação do último bloco.

Os valores de N e T devem ser configuráveis e serão parâmetros de arranque do sequenciador.
Por omissão, devem ser usados N=4 e T=5 segundos.

## 4. Variantes e Entregas do Projeto

O projeto está dividido em três entregas incrementais.

### Entrega A — Sistema Base sem Replicação

Objetivo: Desenvolver uma solução em que o serviço é prestado por um único servidor (ou seja, uma arquitetura cliente-servidor simples, sem replicação de servidores), que aceita pedidos num endereço/porto bem conhecido. Nesta fase é construída a lógica funcional da criptomoeda sem blockchain nem replicação.
Nesta fase assume-se que só existe um nó e que o sequenciador se limita a dar um número de ordem a cada transação (ou seja, não agrupa transações em blocos).
Os clientes interagem com o único nó, que por sua vez atua invoca o sequenciador.

Todos os processos devem usar os blocking stubs do gRPC.

#### Entrega A.1 — Arquitetura Cliente-Servidor

Objetivos:

- Implementar os nós (_fabric peers_) que mantêm o estado da criptomoeda;
- Implementar o cliente, que comunica diretamente com o servidor;
- Todas as operações são imediatamente executadas localmente no servidor.

#### Entrega A.2 — Integração de sequenciador simplificado

Objetivos.

- Implementar o sequenciador central, numa versão simplificada em que não há agrupamento e entrega de blocos.
- O sequenciador mantém uma lista de transações;
- Quando o sequenciador recebe um pedido de difusão de uma transação, insere-a na lista de transações;
- O sequenciador responde aos pedidos de entrega com as transações individuais que já ordenou na sua lista local.
- Quando um nó recebe um pedido de transação, deve enviá-la ao sequenciador e aguardar que essa transação seja entregue atomicamente antes de executar a transação sobre o estado local da _blockchain_ e finalmente retornar ao cliente.

A interface esperada do sequenciador está definida num ficheiro _.proto_ que será disponibilizado pelo corpo docente.

### Entrega B — Blockchain Permissioned com Ordem Total

Objetivos:

- Estender o sistema replicado para que a difusão passe a ser baseada em blocos.
- Introduzir mecanismos de tolerância a falhas silenciosas dos nós, assim como saída e entrada dinâmica de nós.

Nesta solução, o cliente utilizador precisará recorrer aos non-blocking stubs do gRPC.

#### Entrega B.1 — Difusão Atómica baseada em blocos

Objetivos:

- Estender o sequenciador central para que este suporte difusão atómica baseada em blocos ;
- O sequenciador recebe transações individuais e agrupa-as em blocos;
- O sequenciador responde aos pedidos de entrega com os blocos de transações que já ordenou na sua lista local.
- Os nós executam as transacções que constam do bloco recebido actualizando o seu estado local da _blockchain_.

Nesta entrega deverá também:

- Estender o cliente com as variantes não-bloqueantes dos comandos.
- Implementar a funcionalidade de atrasar, no nó, a execução de cada pedido durante um período indicado no cabeçalho do pedido. Esta funcionalidade deverá aproveitar o suporte a envio de metadados em gRPC.

#### Entrega B.2 — Entrada e saída dinâmica de nós, tolerância a falhas dos nós

Objetivos:

- Estender a implementação do nó de forma a que este possa ser lançado a qualquer ponto no tempo. Implica dotar o nó da lógica necessária para, quando se inicia, se sincronizar com o sequenciador para obter a _blockchain_ atual antes de estar apto a receber comandos dos clientes. Deve assumir que o nó não mantém estado persistente. Note-se que este mecanismo implicitamente suporta também cenários em que um nó termina e, posteriormente, reentra no sistema.
- Incorporar mecanismos para permitir que, no caso do cliente suspeitar de falha do nó durante a invocação de um pedido,
  o cliente repita o mesmo pedido a outro nó, mais precisamente o próximo nó na lista de nós conhecidos pelo cliente.
  A transição para um nó alternativo deve assegurar que as garantias de coerência (linearizabilidade) continuam a ser respeitadas.

### Entrega C — Ordem Causal e Segurança

Objetivo: otimizar latência para transferências e introduzir autenticação criptográfica.

#### Entrega C.1 — Ordem Causal para Transferências

Nesta fase, as operações de transferência devem ser tratadas de forma especial:

- Em vez de exigir ordem total para todas as transferências, o sistema deve garantir apenas ordem causal entre transferências relacionadas (a justificação desta optimização está disponível em ["The Consensus Number of a Cryptocurrency", Gerraoui _et al._](https://doi.org/10.1145/3293611.3331589) );
- Sugere-se o uso de mecanismos inspirados em relógios vetoriais ou dependências explícitas;
- Isso permite que o nó confirme transferências ao cliente antes de estas serem entregues pelo sequenciador (num bloco).

As restantes operações (criaCarteira, eliminaCarteira) continuam a exigir ordem total.

#### Entrega C.2 — Assinaturas Digitais

Devem ser adicionados mecanismos básicos de integridade:

- Nos clientes, todas as transações devem ser assinadas digitalmente pelo utilizador emissor;
- Cada nó deve verificar a assinatura antes de aceitar a operação.

No sequenciador:

- Cada bloco produzido deve ser assinado;
- Cada nó deve validar a assinatura do bloco antes de o aplicar.

> No Fabric, é o MSP que oferece a infraestrutura de _PKI_. Como referido anteriormente, o MSP (e a PKI associada) estão fora do âmbito deste projeto. Em alternativa, assume-se que as chaves públicas de cada entidade (utilizadores, sequenciador) estão previamente distribuídas num ficheiro que deve ser lido por cada nó. Adicionalmente, as chaves privadas (dos utilizadores e do sequenciador) estão previamente guardadas em ficheiros aos quais o processo cliente e sequenciador podem aceder (respetivamente).

## 5. Processos

### 5.1. Sequenciador

O sequenciador deve ser lançado recebendo como argumento único o seu porto.
Por exemplo (**$** representa a linha de comando do terminal):

`$ mvn exec:java -Dexec.args="3001"`

A sua interface remota encontra-se definida nos ficheiros _proto_ fornecidos pelo corpo docente juntamente com este enunciado.

### 5.2. Nós

Cada nó é simultaneamente um servidor (pois recebe e responde a pedidos dos clientes) e um cliente (pois efetua invocações remotas ao sequenciador).
Quando é lançado, recebe o porto em que deve oferecer o seu serviço remoto, o nome da sua organização (uma _string_ sem espaços) assim como o nome de máquina e porto do sequenciador com o qual vai interagir.

Por exemplo, para usar o porto 2001, ser um nó da organização "OrgCoin" e ligar-se ao sequenciador em localhost:3001:

`$ mvn exec:java -Dexec.args="2001 OrgCoin localhost:3001"`

### 5.3. Clientes

O programa do cliente recebe como argumentos os nomes das máquinas e portos dos nós conhecidos pelo cliente. Como é explicado de seguida, para maior flexibilidade de testes, cada comando identifica a qual dos nós conhecidos pelo cliente o pedido respetivo deve ser enviado.

Por exemplo, um cliente pode ser lançado assim, ficando a conhecer três nós distintos:

`$ mvn exec:java -Dexec.args="localhost:2001 blockchain.com:3000 moeda.pt:4000"`

Cada cliente recebe comandos a partir do terminal. Deve mostrar o símbolo _>_ sempre que se encontrarem à espera que um comando seja introduzido.

Para todos os comandos, caso não ocorra nenhum erro, o cliente devem imprimir "OK" seguido da mensagem de resposta, tal como gerada pelo método toString() da classe gerada pelo compilador `protoc`, conforme ilustrado nos exemplos abaixo.

No caso em que um comando origina algum erro do lado do servidor, esse erro deve ser transmitido ao cliente usando os mecanismos do gRPC para tratamento de erros (no caso do Java, encapsulados em exceções). Nessas situações, quando o cliente recebe uma exceção após uma invocação remota, este deve simplesmente imprimir (no _stderr_) uma mensagem que descreva o erro correspondente.

Os comandos deverão ser numerados sequencialmente em cada cliente e a impressão do resultado (seja sucesso ou erro) deve ser precedida pelo número do comando.

Os comandos que o cliente aceita pela linha de comandos obedecem às seguintes regras:

- Existe um comando para cada operação do serviço:
  - `C`, que invoca **criaCarteira**, passando os identificadores de utilizador e da carteira como 1º e 2º argumentos,
  - `E`, que invoca **eliminaCarteira**, passando os identificadores de utilizador e da carteira como 1º e 2º argumentos,
  - `T`, que invoca **transfere**, passando os identificadores de utilizador, carteira de origem, carteira de destino e o valor (inteiro positivo), recebidos como 1º, 2º, 3º e 4º argumentos,
  - `S`, que invoca **leSaldo**, passando o identificador de carteira recebido como 1º argumento,
  - `B`, que invoca **leBlockchain**.
- Além dos argumentos referidos acima, há dois argumentos adicionais:
  - Em todos os comandos: Um inteiro que indexa o nó ao qual o pedido deve ser enviado (na ordem dos nós conhecido pelo cliente, começando em 0));
  - Em todos exceto `B`: um inteiro que especifica o atraso em segundos que o nó contactado deve esperar antes de executar o pedido.
- Os nós só deverão dar aos clientes a resposta dos comando `C`, `E` e `T` depois da transação respetiva ter sido entregue atomicamente num bloco da _blockchain_.
- **[Só a partir da entrega B.1]** Deverão ser igualmente implementadas variantes não-bloqueantes destes comandos, usando as minúsculas correspondentes (`c`, `e` e `t`). Estes devem devolver imediatamente à consola (sem imprimir resultado), permitindo assim que novos comandos sejam processados. O resultado dos comandos não-bloqueantes só devem ser impressos posteriormente, assim que o cliente receber a resposta do nó.

Alguns exemplos (todos enviados ao 1º nó conhecido, sem atrasos):

```
> C Alice cA 0 0
1 OK

> C Bob cB 0 0
2 OK

> T BC bc cA 10 0 0
3 OK

> T Alice cA cB 5 0 0
4 OK

> S cA 0 0
5 OK
5

> S cB 0 0
6 OK
5

```

>

Existem também dois comandos adicionais, que não resultam em invocações remotas:

- `P`, que bloqueia o cliente pelo número de segundos passado como único argumento.

- `X`, que termina o cliente.

## 6. Faseamento da execução do projeto

Os alunos poderão optar por desenvolver apenas os objetivos A e B do projecto (nível de dificuldade "Bring 'em on!") ou também o objetivo C (nível de dificuldade "I am Death incarnate!"). Note-se que o nível de dificuldade "Don't hurt me" não está disponível neste projecto.

O nível de dificuldade escolhido afeta a forma como o projeto de cada grupo é avaliado e a cotação máxima que pode ser alcançada (ver Secção 9 deste anunciado).

O projeto prevê 3 entregas. A data final de cada entrega (ou seja, a data de cada entrega) está publicada no site dos laboratórios de SD.

Dependendo do nível de dificuldade seguido, o faseamento das etapas ao longo do tempo será distinto.

### 6.1. Faseamento do nível de dificuldade "Bring 'em on!"

- Entrega 1
  - Etapa A.1

- Entrega 2
  - Etapas A.2 e B.1

- Entrega 3
  - Etapas B.2 e C.1

### 6.2. Faseamento do nível de dificuldade "I am Death incarnate!"

- Entrega 1
  - Etapas A.1 e A.2

- Entrega 2
  - Etapas B.1 e B.2

- Entrega 3
  - Etapas C.1 e C.2

## 7. Tecnologias

Todos os componentes do projeto têm de ser implementados na linguagem de programação [Java](https://docs.oracle.com/javase/specs/).
A ferramenta de construção a usar, obrigatoriamente, é o [Maven](https://maven.apache.org/).
A invocação remota de serviços deve ser suportada por serviços [gRPC](https://grpc.io/). Os serviços implementados devem obedecer aos _protocol buffers_ fornecidos no código base disponível no repositório github do projeto.

## 8. Modelo de Execução e Hipóteses

Assume-se que:

- Os processos cliente não falham;
- O sequenciador não falha;
- Não é exigida persistência em disco.

### 8.1. Opção de Debug

Todos os processos devem aceitar a opção -debug.
Quando ativada, devem ser impressas mensagens no stderr indicando:

- Receção de pedidos;
- Envio de transações;
- Criação e receção de blocos;
- Aplicação de operações ao estado.

## 9. Avaliação

A avaliação segue as seguintes regras:

- Na primeira entrega, o grupo indica (no documento README.md na pasta raíz do seu projeto) qual o nível de dificuldade em que o grupo decidiu trabalhar.
  Nas entregas seguintes, caso pretenda, o grupo pode baixar o nível de dificuldade (ou seja, passar de "I am Death incarnate!" para "Bring 'em on!"). Um grupo que esteja no nível "Bring 'em on!" em qualquer fase do projeto já não poderá subir de nível nas entregas seguintes.

- No final de cada fase, o corpo docente avaliará o conjunto de etapas que são exigidas nessa fase para o nível de dificuldade escolhido pelo grupo nesse momento (ver Secção 3). A classificação obtida pelo grupo nessa fase depende da completude e qualidade do código relevante para esse conjunto de etapas.
  Uma vez avaliada uma etapa no final de uma dada fase, essa etapa já não será reavaliada nas fases seguintes. Por exemplo, se um grupo opta por "I am Death incarnate!" mas submete uma solução incompleta da etapa 1.2 no final da 1ª fase (obtendo uma cotação baixa), os pontos perdidos na cotação dessa etapa já não serão recuperados em fases seguintes (mesmo que o grupo complete ou melhore o código correspondente, ou mesmo que o grupo baixe de nível de dificuldade).

### 9.1. Cotações das etapas

A cotação máxima de cada etapa é a seguinte:

- Etapa A.1: 5 valores
- Etapa A.2: 2 valores
- Etapa B.1: 4 valores
- Etapa B.2: 3 valores
- Etapa C.1: 3 valores
- Etapa C.2: 3 valores

### 9.2. Fotos

Cada membro da equipa tem que atualizar o Fénix com uma foto, com qualidade, tirada nos últimos 2 anos, para facilitar a identificação e comunicação.

### 9.3. Identificador de grupo

O identificador do grupo tem o formato GXX, onde G representa o campus e XX representa o número do grupo de SD atribuído pelo Fénix. Por exemplo, o grupo A22 corresponde ao grupo 22 sediado no campus Alameda; já o grupo T07 corresponde ao grupo 7 sediado no Taguspark.
O grupo deve identificar-se no documento README.md na pasta raiz do projeto.

Em todos os ficheiros de configuração pom.xml e de código-fonte, devem substituir GXX pelo identificador de grupo.

Esta alteração é importante para a gestão de dependências, para garantir que os programas de cada grupo utilizam sempre os módulos desenvolvidos pelo próprio grupo.

### 9.4. Colaboração

O Git é um sistema de controlo de versões do código fonte que é uma grande ajuda para o trabalho em equipa.
Toda a partilha de código para trabalho deve ser feita através do GitHub.

Brevemente, o repositório de cada grupo estará disponível em: https://github.com/tecnico-distsys/GXX-BlockchainIST/ (substituir GXX pelo identificador de grupo).

A atualização do repositório deve ser feita com regularidade, correspondendo à distribuição de trabalho entre os membros da equipa e às várias etapas de desenvolvimento.

Cada elemento do grupo deve atualizar o repositório do seu grupo à medida que vai concluindo as várias tarefas que lhe foram atribuídas.

### 9.5. Entregas

As entregas do projeto serão feitas também através do repositório GitHub.

A cada fase estará associada uma tag.

As tags associadas a cada entrega são SD_Entrega1, SD_Entrega2 e SD_Entrega3, respetivamente. Cada grupo tem que marcar o código que representa cada entrega a realizar com uma tag específica antes da hora limite de entrega.

Para as 2ª e 3ª entregas, além do código da solução, é também exigido que cada grupo submeta um documento (com um máximo de 2 páginas) a descrever o desenho da solução. As _templates_ a usar para esse documentos serão divulgadas pelo corpo docente.

As datas limites de entrega estão definidas no site dos laboratórios: (https://tecnico-distsys.github.io)

### 9.6. Qualidade do código

A avaliação da qualidade engloba os seguintes aspetos:

- Configuração correta (POMs);
- Código legível (incluindo comentários relevantes);
- Tratamento de exceções adequado;
- Sincronização correta;
- Separação das classes geradas pelo protoc/gRPC das classes de domínio mantidas no servidor.

### 9.7. Instalação e demonstração

As instruções de instalação e configuração de todo o sistema, de modo a que este possa ser colocado em funcionamento, devem ser colocadas no documento README.md.
Este documento tem de estar localizado na raiz do projeto e tem que ser escrito em formato MarkDown.

### 9.8. Discussão

As notas das várias partes são indicativas e sujeitas a confirmação na discussão final, na qual todo o trabalho desenvolvido durante o projeto será tido em conta.

A discussão será precedida por um momento em que se pedirá a todos os membros do grupo que, individualmente, apliquem algumas pequenas alteraçãos à funcionalidade de seu projeto (implica editar, recompilar, executar para demonstrar). As alterações pedidas são de dificuldade muito baixa para quem participou ativamente na programação do projeto, e podem ser realizada em poucas linhas de código.

As notas a atribuir serão individuais, por isso é importante que a divisão de tarefas ao longo do trabalho seja equilibrada pelos membros do grupo.

Todas as discussões e revisões de nota do trabalho devem contar com a participação obrigatória de todos os membros do grupo.

Bom trabalho!
