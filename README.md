# BlockchainIST

Distributed Systems Project 2026

**Group A63**

**Difficulty level: I am Death incarnate!**

### Team Members

_(fill the table below with the team members, and then delete this line)_

| Number | Name              | User                                  | Email                                             |
| ------ | ----------------- | ------------------------------------- | ------------------------------------------------- |
| 106900 | Gonçalo Aleixo    | <https://github.com/...>              | <mailto:...@tecnico.ulisboa.pt>                   |
| 110633 | Filipe Oliveira   | <https://github.com/filipeoliveira05> | <mailto:filipe.pinto.oliveira@tecnico.ulisboa.pt> |
| 110720 | Francisco Andrade | <https://github.com/...>              | <mailto:...@tecnico.ulisboa.pt>                   |

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
