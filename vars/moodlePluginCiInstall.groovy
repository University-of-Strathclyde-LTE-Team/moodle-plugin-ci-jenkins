def call(String command = '') {
    def db = env.DB
    if (!db) {
        error('DB environment variable must be set. Are you running moodlePluginCiInstall outside of the container?')
    }

    // The DB env variable can probably be used directly by moodle-plugin-ci, but this lets us check the user hasn't
    // tried to pass it more easily.
    def installParams = [
        'db-type': db,
        'db-host': env.MOODLE_CI_DB_HOST ?: 'localhost',
        'db-user': env.MOODLE_CI_DB_USER ?: 'moodle',
        'db-pass': env.MOODLE_CI_DB_PASS ?: 'moodle',
    ]

    def forbidden = installParams.keySet().findAll { key -> command.contains('--' + key) }
    if (forbidden) {
        def paramList = forbidden.collect { key -> '--' + key }.join(', ')
        error("The following parameters are managed automatically and cannot be passed: ${paramList}")
    }

    def installCommand = ["\$WORKSPACE/ci/bin/moodle-plugin-ci install"]
    installParams.each { key, val ->
        installCommand << "--${key} ${val}"
    }
    installCommand << command

    sh """
        export PATH="\$WORKSPACE_BIN:\$PATH"
        ${installCommand.join(' ')}
    """

    // If MOODLE_BEHAT_WWWROOT is set, withMoodlePluginCiContainer has provided a Selenium
    // container and expects us to provide the web server now that Moodle has been checked
    // out. We detect the Moodle 5.1+ public/ directory structure the same way
    // moodle-plugin-ci does: by checking for public/version.php.
    if (env.MOODLE_BEHAT_WWWROOT) {
        sh '''
            if [ -f "$WORKSPACE/moodle/public/version.php" ]; then
                php -S 0.0.0.0:8000 -t "$WORKSPACE/moodle/public" >/dev/null 2>&1 &
            else
                php -S 0.0.0.0:8000 -t "$WORKSPACE/moodle" >/dev/null 2>&1 &
            fi
        '''
    }
}
