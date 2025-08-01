Code along with the book "Growing Object-Oriented Software Guided by Tests"

## Project structure and dependencies

The book doesn't define any dependency management solution or particular project structure.
I used Gradle as the easiest dependency management tool.

The windowlicker library is pretty old (the last version was published in 2012), but it's available in the Maven repos. 
Smack (XMPP client) was last published in 2011 but is also available.

## Running the XMPP server

The book proposes to use the [Openfire](https://igniterealtime.org/projects/openfire/) server as the XMPP server, but it's tricky to run: 
Docker images aren't available for arm64, and openfire isn't available in nixpkgs.

Trying to use [stabber](https://github.com/profanity-im/stabber) instead, as follows:
```shell
nix-shell -p stabber

stabber -p 5230 -h 5231 -l DEBUG
```

## Nix setup

The project contains a Nix shell setup with direnv. 
Make sure to have Nix and direnv installed, and run `direnv allow` in this directory.