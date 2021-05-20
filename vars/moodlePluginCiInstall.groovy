def call(String command = '') {

    def db = "${DB}"
    if (!db) {
        error("DB environment variable must be set. Are you running moodlePluginCiInstall outside of the container?")
    }

    def installParams = [
        "db-type": db,
        "db-user": "jenkins",
        "db-pass": "jenkins",
        "db-host": "127.0.0.1"
    ]

    installParams.each { key, val ->
        if (command.contains('--' + key)) {
            error("The following parameters cannot be passed: db-user, db-pass, db-host")
        }
    }

    def installCommand = ['moodle-plugin-ci install']
    installParams.each { key, val ->
        installCommand << "--${key} ${val}"
    }
    installCommand << command

    sh installCommand.join(' ')

}