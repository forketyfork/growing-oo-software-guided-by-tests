{
    description = "Development environment setup for the code to the book Growing Object-Oriented Software, Guided by Tests";

    inputs = {
        nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
        flake-utils.url = "github:numtide/flake-utils";
    };

    outputs = { self, nixpkgs, flake-utils }:
        flake-utils.lib.eachDefaultSystem(system:
            let
                pkgs = nixpkgs.legacyPackages.${system};
            in 
            {
                devShells.default = pkgs.mkShell {
                    buildInputs = with pkgs; [
                        stabber
                        jdk21
                        (writeShellScriptBin "stabber-up" "stabber -p 5222 -h 5223 -l DEBUG &")
                        (writeShellScriptBin "stabber-down" "pkill stabber")
                    ];
                    
                    shellHook = ''
                      export JAVA_HOME=${pkgs.jdk21}

                      echo "🚀 Growing OO Software dev environment ready"
                      echo "   Java version: $(java -version 2>&1 | head -1)"
                      echo "   Gradle version: $(gradle --version | grep Gradle)"
                      echo ""
                      echo "📋 Available commands:"
                      echo "   stabber-up/stabber-down - manage stabber"
                    '';
                };
            });

}
