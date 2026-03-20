{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = with pkgs; [
    # JavaScript tooling
    nodejs_22

    # Clojure tooling
    clojure
    babashka

    # Java (required for ClojureScript compilation)
    jdk17

    # Optional: shadow-cljs for build tooling
    # You can install this via npm instead if preferred
  ];

  shellHook = ''
    echo "ClojureScript + React development environment"
    echo "Node version: $(node --version)"
    echo "Clojure version: $(clojure --version)"
    echo "Babashka version: $(bb --version)"
    echo "Java version: $(java -version 2>&1 | head -n 1)"
    echo ""
    echo "Available tasks:"
    echo "  bb dev   - Watch and compile demo"
    echo "  bb test  - Run tests"
  '';
}
