# BlockchainIST

Distributed Systems Project 2026

**Group A63**

**Difficulty level: I am Death incarnate!**

### Team Members

| Number | Name              | User                                  | Email                                                |
| ------ | ----------------- | ------------------------------------- | ---------------------------------------------------- |
| 106900 | Gonçalo Aleixo    | <https://github.com/GoncaloAleixo>    | <mailto:goncalofaleixo@tecnico.ulisboa.pt>           |
| 110633 | Filipe Oliveira   | <https://github.com/filipeoliveira05> | <mailto:filipe.pinto.oliveira@tecnico.ulisboa.pt>    |
| 110720 | Francisco Andrade | <https://github.com/xicoo22>          | <mailto:francisco.santos.andrade@tecnico.ulisboa.pt> |

## Getting Started

The overall system is made up of several modules.
The definition of messages and services is in _Contract_.

See the [Project Statement](https://github.com/tecnico-distsys/BlockchainIST-2026) for a complete domain and system description.

### Prerequisites

The Project is configured with Java 17 (which is only compatible with Maven >= 3.8), but if you want to use Java 11 you
can too -- just downgrade the version in the POMs.

To confirm that you have them installed and which versions they are, run in the terminal:

```s
javac -version
mvn -version
```

### Installation

To compile and install all modules:

```s
mvn clean install
```

## Built With

- [Maven](https://maven.apache.org/) - Build and dependency management tool;
- [gRPC](https://grpc.io/) - RPC framework.

## Execution

### Compile

To compile and install all modules:

```s
mvn clean install
```

### Generate Keys (once)

Generate cryptographic key pairs for all entities (required for C.2):

```s
mvn exec:java -Dexec.mainClass="pt.tecnico.blockchainist.contract.crypto.KeyGeneratorMain" \
    -Dexec.args="keys BC Alice Bob Charlie David Emma Fred Ginger Henry Iris sequencer" -pl contract
```

This creates the `keys/` directory with private key files (`*.priv`) and a `public_keys` file. Only needs to be run once (or whenever keys need to be regenerated).

### Run Sequencer (Terminal 1)

```s
mvn exec:java -Dexec.args="<port> <private_key_file>" -pl sequencer
```

Example: `mvn exec:java -Dexec.args="3001 keys/sequencer.priv" -pl sequencer`

### Run Node(s) (Terminal 2)

```s
mvn exec:java -Dexec.args="<port> <org> <sequencer_host>:<sequencer_port> <public_keys_file>" -pl node
```

Example: `mvn exec:java -Dexec.args="2001 OrgA localhost:3001 keys/public_keys" -pl node`

### Run Client (Terminal 3)

```s
mvn exec:java -Dexec.args="<keys_dir> <host>:<port>:<org> [<host>:<port>:<org> ...]" -pl client
```

Example: `mvn exec:java -Dexec.args="keys localhost:2001:OrgA" -pl client`

## Tests

To run the automated test suite:

1. Generate keys (if not already done): see **Generate Keys** above
2. Start the sequencer: `mvn exec:java -Dexec.args="3001 keys/sequencer.priv" -pl sequencer`
3. Start a node: `mvn exec:java -Dexec.args="2001 OrgA localhost:3001 keys/public_keys" -pl node`
4. Go to the `tests` directory: `cd tests`
5. Run the test script: `./run_tests.sh`

**Note:** The sequencer and node must be restarted between test runs to reset the blockchain state.
