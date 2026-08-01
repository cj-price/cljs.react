{ pkgs, ... }:

let
  jdk = pkgs.jdk25;
in
{
  packages = [
    pkgs.babashka
    pkgs.clj-kondo
    (pkgs.clojure.override { inherit jdk; })
    jdk
    pkgs.nodejs_22
    pkgs.pnpm_10
  ];
}
