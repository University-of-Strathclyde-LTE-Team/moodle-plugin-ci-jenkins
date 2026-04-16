// NOTE: concurrent builds of the same branch are not supported. Each build uses the
// branch's Jenkins workspace directory, and running two builds simultaneously would
// cause them to interfere. Disable concurrent builds in the job configuration.
def call(Map params = [:], Closure body) {
    def phpVersion = params.php ?: '8.3'
    def db         = params.db ?: 'mysql'
    def ciVersion  = params.ciVersion ?: '4'
    def browser    = params.browser

    if (browser && !(browser in ['chrome', 'firefox'])) {
        error("browser must be 'chrome' or 'firefox'")
    }

    def dbType
    switch (db) {
        case 'mysql':    dbType = 'mysqli'; break
        case 'postgres': dbType = 'pgsql';  break
        default:         error("Unsupported db '${db}'. Use 'mysql' or 'postgres'.")
    }

    def network           = UUID.randomUUID().toString()
    def dbContainer       = "${network}-db"
    def seleniumContainer = "${network}-selenium"

    // We call the docker binary directly below rather than using the Docker Pipeline
    // Plugin's Groovy API. The docker-workflow plugin (declared as a requirement in
    // the README) requires the binary to be on PATH anyway, so this is a safe assumption.
    try {
        sh "docker network create '${network}'"

        startDatabase(db, dbContainer, network)

        if (browser) {
            sh """
                docker run -d \
                    --name '${seleniumContainer}' \
                    --network '${network}' \
                    --network-alias selenium \
                    --shm-size=2g \
                    selenium/standalone-${browser}:latest
            """
        }

        sh 'mkdir -p "$WORKSPACE/.home"'

        docker.image("moodlehq/moodle-php-apache:${phpVersion}").inside(
            "--entrypoint '' " +
            "--network '${network}' " +
            "--network-alias moodle " +
            "-e HOME=\$WORKSPACE/.home"
        ) {
            sh 'curl -sS https://getcomposer.org/installer | php -- --install-dir="$WORKSPACE" --filename=composer'
            sh '"$WORKSPACE/composer" --version'

            sh '''
                mkdir -p "$WORKSPACE/.nvm"
                export NVM_DIR="$WORKSPACE/.nvm"
                # PROFILE=/dev/null prevents the installer appending nvm bootstrap lines to
                # any shell rc file — we source nvm.sh manually each time instead.
                # The variable must be set on bash (which runs the install script), not on
                # curl, hence the placement after the pipe.
                curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | PROFILE=/dev/null bash
                # set +x suppresses noisy shell-function traces from both nvm.sh and
                # nvm install — NVM is heavily function-based so set -x floods the log.
                set +x
                . "$NVM_DIR/nvm.sh"
                nvm install --lts
                set -x
            '''

            sh """
                export PATH="\$WORKSPACE:\$PATH"
                export NVM_DIR="\$WORKSPACE/.nvm"
                set +x
                . "\$NVM_DIR/nvm.sh"
                set -x
                # Remove any previous installation so composer create-project starts clean.
                rm -rf "\$WORKSPACE/ci"
                composer create-project -n --no-dev --prefer-dist moodlehq/moodle-plugin-ci ci ^${ciVersion}
            """

            // Node version management is handled entirely by moodle-plugin-ci via NVM:
            // during install its VendorInstaller reads Moodle's .nvmrc, installs the correct
            // version, and writes RUNTIME_NVM_BIN to ci/.env for use by grunt etc.
            // We only need ci/bin (moodle-plugin-ci itself) and WORKSPACE (composer) in PATH.
            def toolPaths = "${env.WORKSPACE}/ci/bin:${env.WORKSPACE}"

            // JENKINS-49076: withEnv cannot reliably update PATH inside docker.image().inside()
            // because the container's own PATH env takes precedence. We store the tool paths
            // in a separate env var and each step that needs them prepends it to PATH itself.
            def envVars = [
                "MOODLE_CI_TOOL_PATHS=${toolPaths}",
                // NVM_DIR must be set so moodle-plugin-ci's VendorInstaller can find NVM,
                // read Moodle's .nvmrc, and install the correct Node version.
                "NVM_DIR=${env.WORKSPACE}/.nvm",
                "DB=${dbType}",
                "MOODLE_CI_DB_HOST=db",
                "MOODLE_CI_DB_USER=moodle",
                "MOODLE_CI_DB_PASS=moodle",
                // Prevent moodle-plugin-ci from attempting to start its own servers.
                "MOODLE_START_BEHAT_SERVERS=false",
            ]

            if (browser) {
                envVars += [
                    "MOODLE_BEHAT_WDHOST=http://selenium:4444/wd/hub",
                    "MOODLE_BEHAT_WWWROOT=http://moodle:8000",
                    "MOODLE_BEHAT_DEFAULT_BROWSER=${browser}",
                ]
            }

            withEnv(envVars) {
                body()
            }
        }
    } finally {
        sh "docker stop '${dbContainer}' || true"
        sh "docker rm -v '${dbContainer}' || true"
        if (browser) {
            sh "docker stop '${seleniumContainer}' || true"
            sh "docker rm -v '${seleniumContainer}' || true"
        }
        sh "docker network rm '${network}' || true"
        cleanWs()
    }
}

private void startDatabase(String db, String containerName, String network) {
    switch (db) {
        case 'mysql':
            sh """
                docker run -d \
                    --name '${containerName}' \
                    --network '${network}' \
                    --network-alias db \
                    -e MYSQL_ROOT_PASSWORD=moodle \
                    --tmpfs /var/lib/mysql:rw,noexec,nosuid \
                    mysql:8.4
            """
            // moodle-plugin-ci creates the database itself, so we start MySQL without
            // MYSQL_DATABASE and grant the moodle user global privileges instead.
            // We poll with docker exec rather than Docker health checks because we need
            // to create the DB user immediately after MySQL accepts connections; health
            // checks would still require a polling loop and split setup into two phases.
            sh """
                ready=0
                for i in \$(seq 1 30); do
                    if docker exec '${containerName}' mysql -uroot -pmoodle -e 'SELECT 1' >/dev/null 2>&1; then
                        docker exec '${containerName}' mysql -uroot -pmoodle \
                            -e "CREATE USER 'moodle'@'%' IDENTIFIED BY 'moodle'; GRANT ALL ON *.* TO 'moodle'@'%'; FLUSH PRIVILEGES;"
                        echo 'MySQL ready.'
                        ready=1
                        break
                    fi
                    echo "Waiting for MySQL (\${i}/30)..."
                    sleep 2
                done
                if [ \$ready -eq 0 ]; then
                    echo 'MySQL did not become ready after 60 seconds.' >&2
                    exit 1
                fi
            """
            break
        case 'postgres':
            sh """
                docker run -d \
                    --name '${containerName}' \
                    --network '${network}' \
                    --network-alias db \
                    -e POSTGRES_USER=moodle \
                    -e POSTGRES_PASSWORD=moodle \
                    --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid \
                    postgres:16
            """
            // Same polling approach as MySQL — see comment above.
            sh """
                ready=0
                for i in \$(seq 1 30); do
                    if docker exec '${containerName}' pg_isready -U moodle >/dev/null 2>&1; then
                        echo 'PostgreSQL ready.'
                        ready=1
                        break
                    fi
                    echo "Waiting for PostgreSQL (\${i}/30)..."
                    sleep 2
                done
                if [ \$ready -eq 0 ]; then
                    echo 'PostgreSQL did not become ready after 60 seconds.' >&2
                    exit 1
                fi
            """
            break
    }
}
