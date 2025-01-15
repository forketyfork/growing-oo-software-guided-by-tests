Code along with the book "Growing Object-Oriented Software Guided by Tests"

## Running the XMPP server

The book proposes to use the [Openfire](https://igniterealtime.org/projects/openfire/) server as the XMPP server, but it's tricky to run: 
Docker images aren't available for arm64, and openfire isn't available in nixpkgs.

Trying to use [stabber](https://github.com/profanity-im/stabber) instead, as follows:
```shell
nix-shell -p stabber

stabber -p 5230 -h 5231 -l DEBUG
```
