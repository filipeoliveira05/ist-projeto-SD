# DOCUMENTO QUE ACOMPANHA A 2ª ENTREGA DO PROJETO DE SD

## 1) Quais dos seguintes requisitos foram corretamente resolvidos? Abaixo de cada requisito, respondam: "Sim" ou "Não" ou "Sim mas com erros: [enumerar sucintamente os erros]"

- Estender o sistema para permitir nós replicados
    - Sim

- Difusão baseada em blocos (e não transações individuais)
    - Sim

- Variantes não-bloqueantes dos comandos
    - Sim

- Funcionalidade de atrasar, no nó, a execução de cada pedido
    - Sim

- Suporte ao lançamento de novos nós a qualquer ponto no tempo
    - Sim

- Tolerância a falhas silenciosas dos nós
    - Sim


## 2) Descrevam sucintamente as principais alterações que o grupo aplicou a cada componente indicada abaixo (máx. 800 palavras). Idealmente, componham uma lista de itens (cada um iniciado por um hífen, tal como a listagem dos requisitos apresentada acima). Refiram ainda se houve alterações aos argumentos de linha de comando dos executáveis, quais foram e qual a sua justificação.

### Aos .proto:

- Adicionado o campo string `request_id` às mensagens `CreateWalletRequest`, `DeleteWalletRequest` e `TransferRequest` em `common.proto`, para suportar retransmissões com idempotência (entrega B.2).
- Adicionada mensagem `Block` em `common.proto`, que contém um campo int32 `block_number` e uma lista `repeated Transaction transactions`, para representar blocos de transações.
- Adicionadas mensagens `DeliverBlockRequest` (com campo `block_number`) e `DeliverBlockResponse` (com campo `Block block`) em `node-sequencer.proto`.
- Adicionado o RPC `DeliverBlock(DeliverBlockRequest) returns (DeliverBlockResponse)` ao serviço `SequencerService`, para entrega de blocos aos nós. O RPC `DeliverTransaction` foi mantido para assegurar compatibilidade com a entrega A.2.

### Ao programa do Cliente:

- Criado um stub assíncrono `NodeServiceGrpc.NodeServiceStub` em `ClientNodeService`, para além do stub bloqueante já existente, para permitir o uso dos comandos não-bloqueantes (c/e/s/t).
- Adicionados métodos assíncronos (`createWalletAsync`, `deleteWalletAsync`, `readBalanceAsync`, `transferAsync`) que recebem um `StreamObserver` e usam o stub assíncrono.
- O atraso `delay-seconds` é enviado como metadado gRPC, via `MetadataUtils.newAttachHeadersInterceptor`, tanto nos pedidos bloqueantes como assíncronos.
- Cada pedido de transação recebe um `requestId`, que foi gerado via `UUID.randomUUID()`, no `CommandProcessor`, para suportar retransmissões com idempotência (entrega B.2).
- Implementada tolerância a falhas com uma lógica retry round-robin, ou seja, quando o cliente deteta uma falha suspeita num nó (erro `UNAVAILABLE` ou `DEADLINE_EXCEEDED`), reenvia o mesmo pedido, com o mesmo `requestId`, ao próximo nó na lista. Isto é feito tanto para comandos bloqueantes (`invokeWithRoundRobinRetry`) como assíncronos (`invokeAsyncWithRoundRobinRetry`).
- O deadline do cliente foi ajustado para `MIN_DEADLINE_SECONDS = 10` com uma margem adicional de 2 segundos sobre o atraso pedido, para acomodar o timeout de bloco do sequenciador, T=5s.
- Adicionado graceful shutdown dos canais gRPC no `finally` do loop de comandos.
- Sem alterações aos argumentos de linha de comando do cliente.

### Ao programa do Nó:

- O nó passou a fazer polling de blocos via `DeliverBlock` em vez de transações individuais. A classe `NodeSequencerClient` foi reescrita e mantém um `nextBlockNumber` e pede blocos sequencialmente ao sequenciador num loop de polling com intervalo de 100ms.
- Adicionado método `syncInitialBlocks()` que no arranque limpa todos os blocos existentes do sequenciador antes de aceitar pedidos de clientes (suporta a entrada dinâmica de nós da entrega B.2).
- Adicionado método `catchUpToLatest()` que é invocado antes de operações de leitura (`readBalance`, `getBlockchainState`) para garantir que o nó lê o estado mais atualizado possível.
- A blockchain local em `NodeState` passou de `List<Transaction>` para `List<Block>`. Adicionado método `getAllTransactions()` que coloca os blocos numa lista plana de transações (para o comando B).
- Criada classe `DelayInterceptor` que implementa `ServerInterceptor` e extrai o header `delay-seconds` dos metadados gRPC e coloca o valor no `Context` do gRPC. O `NodeServiceImpl` lê esse valor via `DELAY_CTX_KEY.get()` e faz `Thread.sleep()` antes de processar cada pedido.
- Criada classe `RequestResult` para cache de resultados de transações já processadas e adicionados mapas `pendingTransactions` com `CompletableFuture` e `completedTransactions` partilhados entre `NodeServiceImpl` e `NodeSequencerClient`, para coordenar a espera pela entrega de blocos e suportar retransmissões com idempotência (entrega B.2).
- Adicionado shutdown hook `Runtime.getRuntime().addShutdownHook` para encerrar bem o servidor gRPC e o canal para o sequenciador (isto foi ainda feedback da primeira entrega).
- Sem alterações aos argumentos de linha de comando do nó.

### Ao programa do Sequenciador:

- O `SequencerState` passou a agrupar transações em blocos segundo a política de criação de blocos, ou seja, um bloco é fechado quando atinge N transações ou quando expira o timer de T segundos após a primeira transação do bloco. Usa um `ScheduledExecutorService` para gerir este timer.
- Adicionado suporte a deduplicação de transações no sequenciador, foi usado um mapa `requestSequenceNumbers` (requestId, seqNumber) que impede que a mesma transação seja adicionada duas vezes ao bloco (isto foi útil para quando o cliente reenvia o pedido a outro nó após uma falha, para a entrega B.2).
- Implementado o método `deliverBlock` no `SequencerServiceImpl` que retorna o bloco com o número pedido pelo nó ou uma resposta vazia se o bloco ainda não estiver completo.
- Adicionado shutdown hook `Runtime.getRuntime().addShutdownHook` para encerrar bem o servidor gRPC (isto foi ainda feedback da primeira entrega).
- **Alteração aos argumentos de linha de comando:** o sequenciador passa a aceitar dois argumentos opcionais: `N` (número máximo de transações por bloco, default 4) e `T` (timeout do bloco em segundos, default 5): `<port> [N] [T]`. Esta alteração foi necessária para configurar a política de criação de blocos conforme o enunciado pedia para esta entrega 2.


## 3) Na vossa solução, as transações recebidas pelo sequenciador levam algum identificador? Se sim, expliquem brevemente o formato e como é gerado o identificador (máx. 100 palavras)

Sim. Cada transação de escrita (createWallet, deleteWallet, transfer) contém um campo string `request_id` definido no `.proto`. O identificador é um UUID v4 gerado no cliente via `UUID.randomUUID().toString()` antes do envio do pedido. O mesmo `request_id` é reutilizado em caso de retry a outro nó (para a entrega B.2), o que permite ao sequenciador detetar duplicados através de um mapa que contém `(requestId, seqNumber)` e devolver o número de sequência original sem adicionar outra vez a transação ao bloco.


## 4) Como foi frisado na primeira aula teórica, não é aceitável o uso de GenAI/Code Copilots para gerar código usado diretamente no projeto. Tendo isto em conta, respondam brevemente às seguintes 3 perguntas:

### i) Os membros do grupo conseguem explicar o código submetido se tal for perguntado na discussão do projeto (feita sem recurso a AI)?

Sim. O código submetido resultou do planeamento interno inicial feito por nós três.

### ii) Os membros do grupo conseguem defender todas as decisões de desenho e implementação se tal for perguntado na discussão do projeto (feita sem recurso a AI)?

Sim. As decisões de desenho foram discutidas por nós internamente após a análise inicial apoiada pela AI. Nós validámos se as sugestões estavam em conformidade com as restrições do projeto.

### iii) Usaram alguma(s) ferramenta(s) de AI? Se sim, para que uso?

Sim, utilizámos AI nos seguintes pontos:

- Decomposição do enunciado em tarefas técnicas detalhadas, convertidas em Issues para facilitar a distribuição de trabalho e a gestão do projeto no GitHub.

- Melhoria da clareza e consistência dos comentários no código e formatação em markdown deste ficheiro de template de modo a seguir as normas exigidas.

- Auxílio na interpretação de stack traces e erros de compilação/testes de modo a identificar causas comuns de falha em comunicações gRPC e concorrência Java, sobretudo considerando que a implementação deste projeto foi feito em três computadores diferentes dos três membros do grupo.

- Auxílio na organização e revisão gramatical da documentação do projeto.