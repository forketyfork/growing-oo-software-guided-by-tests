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
                    ];
                };

                shellHook = ''
                    echo "Run stabber -p 5230 -h 5231 -l DEBUG"
                '';
            });

}
