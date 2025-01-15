Code along with the book "Growing Object-Oriented Software Guided by Tests"

## Project structure and dependencies

The book doesn't define any dependency management solution or particular project structure.
I used Gradle as the easiest dependency management tool.

The windowlicker library is pretty old (~12 years), but it's available in the Maven repos. 
Smack (XMPP client) is also available

## Running the XMPP server

The book proposes to use the [Openfire](https://igniterealtime.org/projects/openfire/) server as the XMPP server, but it's tricky to run: 
Docker images aren't available for arm64, and openfire isn't available in nixpkgs.

Trying to use [stabber](https://github.com/profanity-im/stabber) instead, as follows:
```shell
nix-shell -p stabber

stabber -p 5230 -h 5231 -l DEBUG
```

Might want to add some nix setup to the project to automate this.