# DOCUMENTO QUE ACOMPANHA A 3ª ENTREGA DO PROJETO DE SD

## 1) Quais dos seguintes requisitos foram corretamente resolvidos? Abaixo de cada requisito, respondam: "Sim" ou "Não" ou "Sim mas com erros: [enumerar sucintamente os erros]"

- Suporta a execução/resposta a pedidos de transferência antes da transação correspondente ser entregue pelo sequenciador [C.1].

    - Sim.

- As transações são assinadas digitalmente pelo utilizador que as invocou e as respetivas assinaturas são verificadas antes de cada transação ser executada nas réplicas [C.2].

    - Sim.

- A blockchain gerada pelo sequenciador é assinada digitalmente e as assinaturas correspondentes são verificadas pelos nós que recebem cada bloco [C.2].

    - Sim.


## 2) Descrevam sucintamente as principais alterações que o grupo aplicou a cada componente indicada abaixo (máx. 800 palavras) Idealmente, componham uma lista de itens (cada um iniciado por um hífen, tal como a listagem dos requisitos apresentada acima). Refiram ainda se houve alterações aos argumentos de linha de comando dos executáveis, quais foram e qual a sua justificação.

### Aos .proto:

- Adicionado campo `bytes signature = 4` a `CreateWalletRequest` e `DeleteWalletRequest` para transportar a assinatura digital do utilizador.
- Adicionado campo `bytes signature = 7` a `TransferRequest`, e campo `repeated string causal_dependencies = 6` para transportar as dependências causais da transferência.
- Adicionado campo `bytes signature = 3` a `Block` para transportar a assinatura digital do sequenciador sobre cada bloco.

### Ao programa do Cliente:

- Criada a classe `CryptoUtils` (módulo `contract`, pacote `crypto`) com métodos utilitários para gerar pares de chaves RSA 2048, assinatura (`SHA256withRSA`), verificação, e leitura/escrita de chaves em formato Base64.
- Criada a classe `KeyGeneratorMain` (módulo `contract`) que gera pares de chaves para os 10 utilizadores e o sequenciador, guardando as chaves privadas em ficheiros `.priv` e as chaves públicas num ficheiro único `public_keys`.
- Modificado `ClientMain` para aceitar o diretório de chaves como primeiro argumento de linha de comando (novo formato: `<keys_dir> <host:port:org> [...]`). Carrega as chaves privadas dos utilizadores no arranque e passa-as ao `CommandProcessor`.
- Modificado `CommandProcessor` para assinar cada transação (`createWallet`, `deleteWallet`, `transfer`) com a chave privada do utilizador antes de a enviar ao nó. O request é construído sem assinatura, serializado, assinado, e a assinatura é incluída no pedido enviado.
- [C.1] O `CommandProcessor` mantém um contexto causal (`causalContext`) com os `requestId`s das transações completadas. Antes de cada `transfer`, é feito um snapshot deste contexto e incluído no campo `causal_dependencies` do pedido.
- Modificado `ClientNodeService` para passar o campo `byte[] signature` em todos os métodos de mutação (blocking e async).
- Alteração aos argumentos de linha de comando: o primeiro argumento passou a ser o caminho do diretório de chaves (`keys`), seguido dos endereços dos nós.

### Ao programa do Nó:

- [C.1] Implementada execução especulativa de transferências: o nó aplica a transferência ao estado local imediatamente, responde ao cliente sem esperar pela entrega do bloco pelo sequenciador, e difunde a transação em paralelo. Quando o bloco chega, as transferências já aplicadas especulativamente são ignoradas (sem re-execução).
- [C.1] Implementada espera por dependências causais: antes da execução especulativa, o nó verifica se todas as dependências causais da transferência já foram satisfeitas (presentes em `completedTransactions` ou `speculativeTransfers`), bloqueando com `wait()` até estarem satisfeitas ou ocorrer um timeout.
- [C.2] Modificado `NodeMain` para aceitar o ficheiro de chaves públicas como quarto argumento (`<port> <org> <sequencer> <public_keys_file>`), carregá-las via `CryptoUtils.loadPublicKeys()` e passá-las ao `NodeServiceImpl`.
- [C.2] Modificado `NodeServiceImpl` para verificar a assinatura digital de cada transação recebida (`verifyCreateWalletSignature`, `verifyDeleteWalletSignature`, `verifyTransferSignature`). A verificação ocorre após `applyDelay()` e `validateUserOrganization()`, mas antes da normalização do request. Assinaturas inválidas resultam em erro `UNAUTHENTICATED`.
- [C.2] Modificado `NodeSequencerClient` para verificar a assinatura de cada bloco recebido do sequenciador (`verifyBlockSignature`). Blocos com assinatura inválida são descartados com um aviso.
- Melhorado o mecanismo de polling: o `DeliverBlock` do sequenciador é agora bloqueante. A thread de background fica suspensa até haver um novo bloco, eliminando a espera ativa que acontecia antes (`Thread.sleep`).
- Melhorada a sincronização: substituído `synchronized` nos métodos do `NodeState` por `ReadWriteLock`, permitindo reads paralelos. No `NodeSequencerClient`, removidos locks desnecessários e substituídos por um `blockProcessingLock` dedicado e `AtomicInteger` para o `nextBlockNumber`.
- A sincronização inicial (B.2) já não bloqueia o arranque do servidor: `server.start()` é executado antes do `syncInitialBlocks()`. Writes são aceites imediatamente; apenas reads (`readBalance`, `getBlockchainState`) aguardam a conclusão da sincronização inicial.
- Alteração aos argumentos de linha de comando: adicionado quarto argumento `keys/public_keys` com o caminho para o ficheiro de chaves públicas.

### Ao programa do Sequenciador:

- [C.2] Modificado `SequencerMain` para aceitar o ficheiro da chave privada como último argumento (novo formato: `<port> [N] [T] <private_key_file>`). A chave é carregada via `CryptoUtils.loadPrivateKey()` e passada ao `SequencerState`.
- [C.2] Modificado `SequencerState.closeCurrentBlock()` para assinar cada bloco: constrói o bloco sem assinatura, assina os seus bytes com a chave privada do sequenciador, e reconstrói o bloco incluindo a assinatura.
- Adicionado método `getBlockBlocking()` ao `SequencerState`: quando um nó pede um bloco que ainda não existe, o pedido fica bloqueado (`wait()`) até o bloco ser criado, eliminando a necessidade de polling por parte dos nós.
- Alteração aos argumentos de linha de comando: adicionado argumento obrigatório `keys/sequencer.priv` (último argumento) com o caminho para a chave privada do sequenciador.


## 3) Na vossa solução para o requisito da C.1, em quais condições uma transferência **NÃO** é executada pelo nó (que recebeu o pedido respetivo) antes da transação ser enviada ao sequenciador? (máx. 100 palavras)

Uma transferência não é executada especulativamente quando falha uma das validações prévias: o utilizador não pertence à organização do nó, a assinatura digital é inválida (C.2), ou a transação já foi processada anteriormente (idempotência B.2). Adicionalmente, se a transferência tiver dependências causais (`causal_dependencies`) pendentes, a execução fica bloqueada até essas dependências serem satisfeitas ou expirar o timeout de 15 segundos. Neste último caso, a execução prossegue mesmo assim.



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

- Auxílio na criação de mais testes para testar o código implementado.