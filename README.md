# ScalaFBP

A [Flow-based Programming](https://en.wikipedia.org/wiki/Flow-based_programming) engine written in Scala. The communication with clients uses the [FBP Protocol](https://flowbased.github.io/fbp-protocol), enabling it to be used with the [NoFlo UI](https://github.com/noflo/noflo-ui).

## Quick Start

The quickest way to get the service up is to use [Docker](https://www.docker.com/). An image is available on [Docker Hub](https://registry.hub.docker.com/u/ruippeixotog/scalafbp/) and can be started with the following command:

```
docker run -d -p 3569:3569 ruippeixotog/scalafbp:0.1.0-SNAPSHOT
```

An embedded version of the NoFlo UI will be available on port 3569. After you click the "Login" button (no authentication is needed), the "ScalaFBP Runtime" will appear on the list of runtimes and you'll be ready to create projects using it.

## Architecture Overview

### API

This server provides three main HTTP services:

* The WebSocket endpoint speaking the FBP protocol is served at ws://HOST:3569;
* The UI is available at http://HOST:3569;
* A very basic runtime registry runs at http://HOST:3569/registry.

The UI is configured to use the server's own registry (instead of the public [Flowhub Registry](https://flowhub.io/)) to discover the available runtimes. The ScalaFBP runtime, in turn, registers itself there, making it immediatly available to the UI.

This separation of concerns means ScalaFBP can also be used in Flowhub by registering its WebSocket endpoint as a new runtime there. It also means other runtimes can be added and used by the local UI by registering themselves in the ScalaFBP local registry.

### Source Code

The source code is organized in four main packages, which can be thought of as layers:

* `http`: provides the routes for the HTTP server. The WebSocket route uses the `protocol` layer to handle incoming and outgoing messages;
* `protocol`: handles the (de)serialization and interpretation of FBP WebSocket messages, converting them into meaningful actions for the `runtime` layer;
* `runtime`: contains the core logic and structures for the runtime, such as a component registry, a graph store and a network runner;
* `component`: provides the core models and structures for implementing components, as well as built-in component implementations. The components are registered in `runtime`-layer registries for usage in graphs.

## Extending

Currently, implementing and making new components available to the runtime requires cloning this repository and adding manually new implementations. In the future, I intend to make it easier to create independent packages of components that can be loaded into ScalaFBP.

## Copyright

Copyright (c) 2016 Rui Gonçalves. See LICENSE for details.
