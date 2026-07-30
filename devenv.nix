{ pkgs, ... }:

{
  packages = [
    pkgs.babashka
    pkgs.clj-kondo
    pkgs.clojure
    pkgs.jdk25
    pkgs.nodejs_22
    pkgs.pnpm_10
  ];
}
