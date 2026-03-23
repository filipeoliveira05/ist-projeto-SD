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

### Run Sequencer (Terminal 1)

To run the sequencer (required for A.2):

```s
mvn exec:java -Dexec.args="<port>" -pl sequencer
```

Example: `mvn exec:java -Dexec.args="3001" -pl sequencer`

### Run Node(s) (Terminal 2)

To run a node:

```s
mvn exec:java -Dexec.args="<port> <org> <sequencer_host>:<sequencer_port>" -pl node
```

Example: `mvn exec:java -Dexec.args="2001 OrgA localhost:3001" -pl node`

### Run Client (Terminal 3)

To run a client:

```s
mvn exec:java -Dexec.args="<host>:<port>:<org> [<host>:<port>:<org> ...]" -pl client
```

Example: `mvn exec:java -Dexec.args="localhost:2001:OrgA" -pl client`

## Tests

To run the automated test suite:

1. Go to the `tests` directory: `cd tests`
2. Run the test script: `./run_tests.sh`

**Note:** Ensure that a node (and sequencer for A.2) are running in other terminals before executing the tests. The script only launches the client.
