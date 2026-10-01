# Sandbox kit: Java, Maven, Quarkus CLI and JBang

Commands, in order, to create a Docker Sandbox kit that installs Java, Maven, the Quarkus CLI and JBang through SDKMAN. Run them on your host.

The spec follows Docker's [kit spec reference](https://docs.docker.com/ai/sandboxes/customize/kit-reference/). The install commands and the network allow list haven't been run. If an install step fails, see step 6.

```bash
# 1. Create the kit directory (outside this repo)
mkdir -p ~/kits/java-quarkus-toolchain
cd ~/kits/java-quarkus-toolchain

# 2. Write spec.yaml (the quoted 'EOF' keeps ${{ }} and $SDKMAN_DIR from being expanded)
cat > spec.yaml <<'EOF'
schemaVersion: "2"
kind: mixin
name: java-quarkus-toolchain
version: "1.0.0"
displayName: Java, Maven, Quarkus CLI and JBang
description: JDK, Maven, Quarkus CLI and JBang installed via SDKMAN.

args:
  java:
    default: "25-tem"
    pattern: '^[0-9A-Za-z.+-]+$'

permissions:
  network:
    allow:
      - archive.ubuntu.com
      - security.ubuntu.com
      - ports.ubuntu.com
      - get.sdkman.io
      - api.sdkman.io
      - "*.sdkman.io"
      - github.com
      - "*.githubusercontent.com"
      - api.adoptium.net
      - archive.apache.org
      - dlcdn.apache.org
      - repo1.maven.org
      - repo.maven.apache.org
      - sh.jbang.dev
      - registry.quarkus.io
      - code.quarkus.io

setup:
  install:
    - description: OS packages SDKMAN needs
      user: "0"
      command: apt-get update && apt-get install -y --no-install-recommends curl zip unzip ca-certificates
    - description: Install SDKMAN without touching shell rc files
      user: "1000"
      command: curl -s "https://get.sdkman.io?rcupdate=false" | bash
    - description: Install the toolchain non-interactively
      user: "1000"
      command: >-
        bash -c 'source ~/.sdkman/bin/sdkman-init.sh &&
        sed -i "s/sdkman_auto_answer=false/sdkman_auto_answer=true/" ~/.sdkman/etc/config &&
        sdk install java ${{ kit.args.java }} &&
        sdk install maven && sdk install quarkus && sdk install jbang'
    - description: Put the tools on PATH for every shell (core init only, no completions)
      user: "0"
      command: >-
        printf '%s\n'
        'export SDKMAN_DIR=/home/agent/.sdkman'
        '[[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"'
        >> /etc/sandbox-persistent.sh
EOF

# 3. Validate and inspect the kit
cd ..
sbx kit validate ./java-quarkus-toolchain
sbx kit inspect ./java-quarkus-toolchain --json

# 4. Start a sandbox with the kit (run from the repo so it becomes the workspace)
cd <path-to-this-repo>
sbx run claude --name moby-bank-quarkus --kit ~/kits/java-quarkus-toolchain
#    different JDK? add:  --kit-arg java=21-tem

# 5. Inside the sandbox, verify
java -version && mvn -v && quarkus --version && jbang --version

# 6. If an install step fails on a blocked host (run on the host)
sbx policy log
#    add the blocked host to permissions.network.allow in spec.yaml, then recreate the sandbox

# 7. Optional: package or publish
sbx kit pack ./java-quarkus-toolchain -o java-quarkus-toolchain.zip
sbx kit push ./java-quarkus-toolchain ghcr.io/<org>/java-quarkus-toolchain:1.0
```

## Notes

- `25-tem` is a guess at the SDKMAN identifier for Temurin 25. Check it with `sdk list java`.
- Kits can't be changed on a running sandbox. After editing the spec, recreate the sandbox.
- `sbx kit push` needs `sbx login` first.
- The last install step appends only the SDKMAN init script to `/etc/sandbox-persistent.sh`. Never add bash completion scripts there: the file is sourced before every command, and completions break the shell.
