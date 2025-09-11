# Growing Object-Oriented Software Guided by Tests

[![Build status](https://github.com/forketyfork/growing-oo-software-guided-by-tests/actions/workflows/build.yml/badge.svg)](https://github.com/forketyfork/growing-oo-software-guided-by-tests/actions/workflows/build.yml)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/language-Java-orange.svg)](https://www.oracle.com/java/)

Code along with the book "Growing Object-Oriented Software Guided by Tests"

## Project structure and dependencies

The book doesn't define any dependency management solution or particular project structure.
I used Gradle as the easiest dependency management tool.

The windowlicker library is pretty old (the last version was published in 2012), but it's available in the Maven repos. 
Smack (XMPP client) was last published in 2011 but is also available.

## On the XMPP server

The book proposes to use the [Openfire](https://igniterealtime.org/projects/openfire/) server as the XMPP server, but it's tricky to run: 
Docker images aren't available for arm64, and openfire isn't available in nixpkgs.
Therefore, a simple XMPP server was implemented directly in the project source code.

I ended up "growing" the test XMPP server implementation alongside the project, which turned out
to be a nice "exercise for the reader".

## Project timeline

I'm tagging specific commits with tags like `page-101` as I go, corresponding to the book page.
I'm also keeping the `TODO.md` file aligned with the book's to-do list.

## Nix setup

The project contains a Nix shell setup with direnv. 
Make sure to have Nix and direnv installed, and run `direnv allow` in this directory.