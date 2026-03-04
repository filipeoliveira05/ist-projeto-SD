.
├── README.md
├── client
│   ├── pom.xml
│   ├── src
│   │   └── main
│   │   └── java
│   │   └── pt
│   │   └── tecnico
│   │   └── blockchainist
│   │   └── client
│   │   ├── ClientMain.java
│   │   ├── CommandProcessor.java
│   │   └── grpc
│   │   └── ClientNodeService.java
│   └── target
│   ├── classes
│   │   └── pt
│   │   └── tecnico
│   │   └── blockchainist
│   │   └── client
│   │   ├── ClientMain.class
│   │   ├── CommandProcessor.class
│   │   └── grpc
│   │   └── ClientNodeService.class
│   ├── client-1.0.0-SNAPSHOT.jar
│   ├── generated-sources
│   │   └── annotations
│   ├── maven-archiver
│   │   └── pom.properties
│   └── maven-status
│   └── maven-compiler-plugin
│   └── compile
│   └── default-compile
│   ├── createdFiles.lst
│   └── inputFiles.lst
├── contract
│   ├── pom.xml
│   ├── src
│   │   └── main
│   │   └── proto
│   │   ├── client-node.proto
│   │   ├── common.proto
│   │   └── node-sequencer.proto
│   └── target
│   ├── classes
│   │   ├── client-node.proto
│   │   ├── common.proto
│   │   ├── node-sequencer.proto
│   │   └── pt
│   │   └── tecnico
│   │   └── blockchainist
│   │   └── contract
│   │   ├── BroadcastRequest$1.class
│       │                   ├── BroadcastRequest$Builder.class
│   │   ├── BroadcastRequest.class
│   │   ├── BroadcastRequestOrBuilder.class
│   │   ├── BroadcastResponse$1.class
│       │                   ├── BroadcastResponse$Builder.class
│   │   ├── BroadcastResponse.class
│   │   ├── BroadcastResponseOrBuilder.class
│   │   ├── ClientNode.class
│   │   ├── Common.class
│   │   ├── CreateWalletRequest$1.class
│       │                   ├── CreateWalletRequest$Builder.class
│   │   ├── CreateWalletRequest.class
│   │   ├── CreateWalletRequestOrBuilder.class
│   │   ├── CreateWalletResponse$1.class
│       │                   ├── CreateWalletResponse$Builder.class
│   │   ├── CreateWalletResponse.class
│   │   ├── CreateWalletResponseOrBuilder.class
│   │   ├── DeleteWalletRequest$1.class
│       │                   ├── DeleteWalletRequest$Builder.class
│   │   ├── DeleteWalletRequest.class
│   │   ├── DeleteWalletRequestOrBuilder.class
│   │   ├── DeleteWalletResponse$1.class
│       │                   ├── DeleteWalletResponse$Builder.class
│   │   ├── DeleteWalletResponse.class
│   │   ├── DeleteWalletResponseOrBuilder.class
│   │   ├── DeliverTransactionRequest$1.class
│       │                   ├── DeliverTransactionRequest$Builder.class
│   │   ├── DeliverTransactionRequest.class
│   │   ├── DeliverTransactionRequestOrBuilder.class
│   │   ├── DeliverTransactionResponse$1.class
│       │                   ├── DeliverTransactionResponse$Builder.class
│   │   ├── DeliverTransactionResponse.class
│   │   ├── DeliverTransactionResponseOrBuilder.class
│   │   ├── GetBlockchainStateRequest$1.class
│       │                   ├── GetBlockchainStateRequest$Builder.class
│   │   ├── GetBlockchainStateRequest.class
│   │   ├── GetBlockchainStateRequestOrBuilder.class
│   │   ├── GetBlockchainStateResponse$1.class
│       │                   ├── GetBlockchainStateResponse$Builder.class
│   │   ├── GetBlockchainStateResponse.class
│   │   ├── GetBlockchainStateResponseOrBuilder.class
│   │   ├── NodeSequencer.class
│   │   ├── NodeServiceGrpc$1.class
│       │                   ├── NodeServiceGrpc$2.class
│       │                   ├── NodeServiceGrpc$3.class
│       │                   ├── NodeServiceGrpc$AsyncService.class
│   │   ├── NodeServiceGrpc$MethodHandlers.class
│       │                   ├── NodeServiceGrpc$NodeServiceBaseDescriptorSupplier.class
│   │   ├── NodeServiceGrpc$NodeServiceBlockingStub.class
│       │                   ├── NodeServiceGrpc$NodeServiceFileDescriptorSupplier.class
│   │   ├── NodeServiceGrpc$NodeServiceFutureStub.class
│       │                   ├── NodeServiceGrpc$NodeServiceImplBase.class
│   │   ├── NodeServiceGrpc$NodeServiceMethodDescriptorSupplier.class
│       │                   ├── NodeServiceGrpc$NodeServiceStub.class
│   │   ├── NodeServiceGrpc.class
│   │   ├── ReadBalanceRequest$1.class
│       │                   ├── ReadBalanceRequest$Builder.class
│   │   ├── ReadBalanceRequest.class
│   │   ├── ReadBalanceRequestOrBuilder.class
│   │   ├── ReadBalanceResponse$1.class
│       │                   ├── ReadBalanceResponse$Builder.class
│   │   ├── ReadBalanceResponse.class
│   │   ├── ReadBalanceResponseOrBuilder.class
│   │   ├── SequencerServiceGrpc$1.class
│       │                   ├── SequencerServiceGrpc$2.class
│       │                   ├── SequencerServiceGrpc$3.class
│       │                   ├── SequencerServiceGrpc$AsyncService.class
│   │   ├── SequencerServiceGrpc$MethodHandlers.class
│       │                   ├── SequencerServiceGrpc$SequencerServiceBaseDescriptorSupplier.class
│   │   ├── SequencerServiceGrpc$SequencerServiceBlockingStub.class
│       │                   ├── SequencerServiceGrpc$SequencerServiceFileDescriptorSupplier.class
│   │   ├── SequencerServiceGrpc$SequencerServiceFutureStub.class
│       │                   ├── SequencerServiceGrpc$SequencerServiceImplBase.class
│   │   ├── SequencerServiceGrpc$SequencerServiceMethodDescriptorSupplier.class
│       │                   ├── SequencerServiceGrpc$SequencerServiceStub.class
│   │   ├── SequencerServiceGrpc.class
│   │   ├── Transaction$1.class
│       │                   ├── Transaction$2.class
│       │                   ├── Transaction$Builder.class
│   │   ├── Transaction$OperationCase.class
│       │                   ├── Transaction.class
│       │                   ├── TransactionOrBuilder.class
│       │                   ├── TransferRequest$1.class
│       │                   ├── TransferRequest$Builder.class
│   │   ├── TransferRequest.class
│   │   ├── TransferRequestOrBuilder.class
│   │   ├── TransferResponse$1.class
│       │                   ├── TransferResponse$Builder.class
│   │   ├── TransferResponse.class
│   │   └── TransferResponseOrBuilder.class
│   ├── contract-1.0.0-SNAPSHOT.jar
│   ├── generated-sources
│   │   ├── annotations
│   │   └── protobuf
│   │   ├── grpc-java
│   │   │   └── pt
│   │   │   └── tecnico
│   │   │   └── blockchainist
│   │   │   └── contract
│   │   │   ├── NodeServiceGrpc.java
│   │   │   └── SequencerServiceGrpc.java
│   │   └── java
│   │   └── pt
│   │   └── tecnico
│   │   └── blockchainist
│   │   └── contract
│   │   ├── BroadcastRequest.java
│   │   ├── BroadcastRequestOrBuilder.java
│   │   ├── BroadcastResponse.java
│   │   ├── BroadcastResponseOrBuilder.java
│   │   ├── ClientNode.java
│   │   ├── Common.java
│   │   ├── CreateWalletRequest.java
│   │   ├── CreateWalletRequestOrBuilder.java
│   │   ├── CreateWalletResponse.java
│   │   ├── CreateWalletResponseOrBuilder.java
│   │   ├── DeleteWalletRequest.java
│   │   ├── DeleteWalletRequestOrBuilder.java
│   │   ├── DeleteWalletResponse.java
│   │   ├── DeleteWalletResponseOrBuilder.java
│   │   ├── DeliverTransactionRequest.java
│   │   ├── DeliverTransactionRequestOrBuilder.java
│   │   ├── DeliverTransactionResponse.java
│   │   ├── DeliverTransactionResponseOrBuilder.java
│   │   ├── GetBlockchainStateRequest.java
│   │   ├── GetBlockchainStateRequestOrBuilder.java
│   │   ├── GetBlockchainStateResponse.java
│   │   ├── GetBlockchainStateResponseOrBuilder.java
│   │   ├── NodeSequencer.java
│   │   ├── ReadBalanceRequest.java
│   │   ├── ReadBalanceRequestOrBuilder.java
│   │   ├── ReadBalanceResponse.java
│   │   ├── ReadBalanceResponseOrBuilder.java
│   │   ├── Transaction.java
│   │   ├── TransactionOrBuilder.java
│   │   ├── TransferRequest.java
│   │   ├── TransferRequestOrBuilder.java
│   │   ├── TransferResponse.java
│   │   └── TransferResponseOrBuilder.java
│   ├── maven-archiver
│   │   └── pom.properties
│   ├── maven-status
│   │   └── maven-compiler-plugin
│   │   └── compile
│   │   └── default-compile
│   │   ├── createdFiles.lst
│   │   └── inputFiles.lst
│   ├── protoc-dependencies
│   │   ├── 1dd736ddd8066dc3fee57c8a05f86243
│   │   │   └── google
│   │   │   └── protobuf
│   │   │   ├── any.proto
│   │   │   ├── api.proto
│   │   │   ├── descriptor.proto
│   │   │   ├── duration.proto
│   │   │   ├── empty.proto
│   │   │   ├── field_mask.proto
│   │   │   ├── source_context.proto
│   │   │   ├── struct.proto
│   │   │   ├── timestamp.proto
│   │   │   ├── type.proto
│   │   │   └── wrappers.proto
│   │   └── 6eb4ce6fcd054aa09c92a14a8a36e23b
│   │   └── google
│   │   ├── api
│   │   │   ├── annotations.proto
│   │   │   ├── auth.proto
│   │   │   ├── backend.proto
│   │   │   ├── billing.proto
│   │   │   ├── client.proto
│   │   │   ├── config_change.proto
│   │   │   ├── consumer.proto
│   │   │   ├── context.proto
│   │   │   ├── control.proto
│   │   │   ├── distribution.proto
│   │   │   ├── documentation.proto
│   │   │   ├── endpoint.proto
│   │   │   ├── error_reason.proto
│   │   │   ├── field_behavior.proto
│   │   │   ├── http.proto
│   │   │   ├── httpbody.proto
│   │   │   ├── label.proto
│   │   │   ├── launch_stage.proto
│   │   │   ├── log.proto
│   │   │   ├── logging.proto
│   │   │   ├── metric.proto
│   │   │   ├── monitored_resource.proto
│   │   │   ├── monitoring.proto
│   │   │   ├── quota.proto
│   │   │   ├── resource.proto
│   │   │   ├── routing.proto
│   │   │   ├── service.proto
│   │   │   ├── source_info.proto
│   │   │   ├── system_parameter.proto
│   │   │   ├── usage.proto
│   │   │   └── visibility.proto
│   │   ├── cloud
│   │   │   ├── audit
│   │   │   │   └── audit_log.proto
│   │   │   └── extended_operations.proto
│   │   ├── geo
│   │   │   └── type
│   │   │   └── viewport.proto
│   │   ├── logging
│   │   │   └── type
│   │   │   ├── http_request.proto
│   │   │   └── log_severity.proto
│   │   ├── longrunning
│   │   │   └── operations.proto
│   │   ├── rpc
│   │   │   ├── code.proto
│   │   │   ├── context
│   │   │   │   ├── attribute_context.proto
│   │   │   │   └── audit_context.proto
│   │   │   ├── error_details.proto
│   │   │   └── status.proto
│   │   └── type
│   │   ├── calendar_period.proto
│   │   ├── color.proto
│   │   ├── date.proto
│   │   ├── datetime.proto
│   │   ├── dayofweek.proto
│   │   ├── decimal.proto
│   │   ├── expr.proto
│   │   ├── fraction.proto
│   │   ├── interval.proto
│   │   ├── latlng.proto
│   │   ├── localized_text.proto
│   │   ├── money.proto
│   │   ├── month.proto
│   │   ├── phone_number.proto
│   │   ├── postal_address.proto
│   │   ├── quaternion.proto
│   │   └── timeofday.proto
│   └── protoc-plugins
│   ├── protoc-3.25.1-linux-x86_64.exe
│   └── protoc-gen-grpc-java-1.60.0-linux-x86_64.exe
├── enunciado.md
├── file-structure.md
├── node
│   ├── pom.xml
│   ├── src
│   │   └── main
│   │   └── java
│   │   └── pt
│   │   └── tecnico
│   │   └── blockchainist
│   │   └── node
│   │   ├── NodeMain.java
│   │   ├── NodeSequencerClient.java
│   │   ├── NodeServiceImpl.java
│   │   └── domain
│   │   └── NodeState.java
│   └── target
│   ├── classes
│   │   └── pt
│   │   └── tecnico
│   │   └── blockchainist
│   │   └── node
│   │   ├── NodeMain.class
│   │   ├── NodeSequencerClient$1.class
│   │   ├── NodeSequencerClient.class
│   │   ├── NodeServiceImpl.class
│   │   └── domain
│   │   └── NodeState.class
│   ├── generated-sources
│   │   └── annotations
│   ├── maven-archiver
│   │   └── pom.properties
│   ├── maven-status
│   │   └── maven-compiler-plugin
│   │   └── compile
│   │   └── default-compile
│   │   ├── createdFiles.lst
│   │   └── inputFiles.lst
│   └── node-1.0.0-SNAPSHOT.jar
├── pom.xml
├── sequencer
│   ├── pom.xml
│   ├── src
│   │   └── main
│   │   └── java
│   │   └── pt
│   │   └── tecnico
│   │   └── blockchainist
│   │   └── sequencer
│   │   ├── SequencerMain.java
│   │   ├── SequencerServiceImpl.java
│   │   └── domain
│   │   └── SequencerState.java
│   └── target
│   ├── classes
│   │   └── pt
│   │   └── tecnico
│   │   └── blockchainist
│   │   └── sequencer
│   │   ├── SequencerMain.class
│   │   ├── SequencerServiceImpl.class
│   │   └── domain
│   │   └── SequencerState.class
│   ├── generated-sources
│   │   └── annotations
│   ├── maven-archiver
│   │   └── pom.properties
│   ├── maven-status
│   │   └── maven-compiler-plugin
│   │   └── compile
│   │   └── default-compile
│   │   ├── createdFiles.lst
│   │   └── inputFiles.lst
│   └── sequencer-1.0.0-SNAPSHOT.jar
└── tests
├── README.md
├── inputs
│   ├── input01.txt
│   ├── input02.txt
│   ├── input03.txt
│   └── input04.txt
├── outputs
│   ├── out01.txt
│   ├── out02.txt
│   ├── out03.txt
│   └── out04.txt
├── run_tests.sh
└── test-outputs
├── out01.txt
├── out02.txt
├── out03.txt
└── out04.txt

119 directories, 263 files
