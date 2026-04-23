def call(String command = '') {

    def db = "${DB}"
    if (!db) {
        error("DB environment variable must be set. Are you running moodlePluginCiInstall outside of the container?")
    }

    if (env.TAG) {
        def tag = "${TAG}"
        if (tag && command.contains('--plugin ')) {
            // We split the command on --plugin  so that element 1 contains the path.
            def initial = command.split('--plugin ')
            // The path will not be in element 1, but we need to ensure there is nothing following the path,
            // We assume any following command will have at least a - before it. The path will then be in
             // the initial element.
            def second = initial[1].split(' -')
            // We now spit the path on the directory separator
            def path = second[0].split('/')
            // Find the real first level of the directory, then copy everything in it.
            def directory = (path[0] == '.') ? path[1] : path[0]
            // The files will be one directory level down.
            sh "cp -r ../${directory} ${directory}"
        }
    }

    // The DB env variable can probably be used directly by moodle-plugin-ci, but this lets us check the user hasn't
    // tried to pass it more easily.
    def installParams = [
        "db-type": db,
        "db-user": "jenkins",
        "db-pass": "jenkins",
        "db-host": "127.0.0.1"
    ]

    installParams.each { key, val ->
        if (command.contains('--' + key)) {
            error("The following parameters cannot be passed: db-type, db-user, db-pass, db-host")
        }
    }

    def installCommand = ['moodle-plugin-ci install']
    installParams.each { key, val ->
        installCommand << "--${key} ${val}"
    }
    installCommand << command

    sh installCommand.join(' ')

}