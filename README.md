# xplane-udp

Experiments with a UDP client for [X-Plane](http://www.x-plane.com), 
an awesome flight simulator.

The development of this library and samples based on it is streamed live on 
[Twitch](https://www.twitch.tv/pweezno)

# Purpose

The purpose of this project is to provide a Scala library with a good API to access
X-Plane easily via UDP.

This enables you to read a lot of "data refs" from X-Plane, thus enabling you to 
track your flights, and also to remote control the simulation.

# Status

The root project is a library to access X-Plane via UDP. It provides all the basic
functionality to retrieve and set datarefs and positions.

The `samples` project contains a very basic X-Plane live map sample, using the library
from the root project.

# Prerequisites

- have some Scala knowledge
- have a version of X-Plane (tested with X-Plane 11)

# Building

- install [sbt](http://www.scala-sbt.org/)
- run `sbt`
- you can now run the tests via `test`, or package the library via `package`

# Running the sample

- make sure to have X-Plane running
- via `project samples`, switch to the `samples` sbt subproject
- type `run` to run the main program

# Contributing

If you want to contribute, go ahead, just create a PR, 
I'll be happy to take a look at it.

Please don't always expect prompt responses, though, 
as this is just a small hobby project of mine.

# Maintainers

- [Joachim Hofer](https://github.com/jmhofer)

# License

GPLv3, see [LICENSE](https://github.com/jmhofer/xplane-udp/blob/master/LICENSE) 
for details.
