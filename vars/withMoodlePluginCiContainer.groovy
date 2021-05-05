def call(Map pipelineParams = [:], Closure body) {

    def php = pipelineParams.php ?: '7.2'
    def db = pipelineParams.db ?: 'mysql'
    def runInstall = pipelineParams.containsKey('withInstall')
    def withInstall =  pipelineParams.withInstall
    def withBehatServers = pipelineParams.withBehatServers

    echo "PHP: ${php}"
    echo "Database: ${db}"
    echo "runInstall: ${runInstall}"
    echo "withInstall: ${withInstall}"
    echo "withBehatServers: ${withBehatServers}"

    if (withBehatServers && !runInstall) {
        error("withBehatServers can only be specified if install is run")
    }

    def installParams = [
        "db-type": null,
        "db-user": "jenkins",
        "db-pass": "jenkins",
        "db-host": "127.0.0.1"
    ]

    // Check that none of the controlled parameters has been passed.
    if (runInstall) {
        installParams.each { key, val ->
            if (withInstall.contains('--' + key)) {
                error("The following parameters cannot be passed: db-type, db-user, db-pass, db-host")
            }
        }
    }

    // Validate the database value before building the image.
    switch (db) {
        case 'mysql':
            installParams['db-type'] = 'mysqli'
            break
        case 'postgres':
            installParams['db-type'] = 'pgsql'
            break
        default:
            error("Unknown db type ${db}. Supported types: mysqli, postgres")
    }

    def dockerFileContents = libraryResource 'uk/ac/strath/myplace/Dockerfile'

    // Docker does not like upper case letters in tags.
    def buildTag = "${BUILD_TAG}".toLowerCase()

    // The BUILD_TAG documentation says slashes are replaced by dashes but this seems to be wrong (in Jenkins 2.263.4)
    buildTag = buildTag.replace('%2f', '-')

    // Create Dockerfile in its own directory to prevent unnecessary context being sent.
    def dockerDir = "${buildTag}-docker"
    def image = null
    dir(dockerDir) {
        writeFile(file: 'Dockerfile', text: dockerFileContents)
        image = docker.build(buildTag)
    }

    // Create composer and npm cache directories if they don't exist.
    sh 'mkdir -p ${WORKSPACE}/.npm && chmod 777 ${WORKSPACE}/.npm \
            && mkdir -p ${WORKSPACE}/.composer && chmod 777 ${WORKSPACE}/.composer'

    // Nasty hack to get around the fact that we can't use withEnv to change the PATH on a container
    // (or any other method as far as I can see)
    // https://issues.jenkins.io/browse/JENKINS-49076
    def originalDockerPath = "/var/lib/nvm/versions/node/v14.15.0/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    def pathOnDocker = "${WORKSPACE}/ci/bin:${originalDockerPath}"

    sh "docker network create ${buildTag}"

    if (withBehatServers) {
        sh "docker run -d --rm --name=${buildTag}-selenium --network=${buildTag} --network-alias=selenium --shm-size=2g selenium/standalone-chrome:3"
    }

    image.inside("-e PATH=${pathOnDocker} --network ${buildTag} --network-alias=moodle") {

        // Start database.
        switch (db) {
            case 'mysql':
                sh 'sudo service mysql start'
                break
            case 'postgres':
                sh 'sudo service postgresql start'
                break
            default:
                error("Unknown db type ${db}. Supported types: mysql, postgres")
        }

        sh "sudo update-alternatives --set php /usr/bin/php${php}"

        // Preload env file with variables to work around withEnv not apparently being picked up by symfony.
        // This shouldn't be necessary so we should get rid of it once we understand the problem.
        def envFile = new File("$WORKSPACE/ci/.env")
        envFile << "MOODLE_BEHAT_WDHOST=http://selenium-chrome:4444/wd/hub"
        envFile << "MOODLE_BEHAT_WWWROOT=http://moodle:8000"

        // Set composer and npm directories to allow caching of downloads between jobs.
        def installEnv = ["npm_config_cache=${WORKSPACE}/.npm", "COMPOSER_CACHE_DIR=${WORKSPACE}/.composer"]
        if (withBehatServers) {
            installEnv << "MOODLE_BEHAT_WDHOST=http://selenium-chrome:4444/wd/hub"
            installEnv << "MOODLE_BEHAT_WWWROOT=http://moodle:8000"
        }
        withEnv(installEnv) {

            // Install plugin ci.
            sh 'composer create-project -n --no-dev --prefer-dist moodlehq/moodle-plugin-ci ci ^3'
        }

        if (runInstall) {
            def installCommand = ['moodle-plugin-ci install']
            installParams.each { key, val ->
                installCommand << "--${key} ${val}"
            }
            installCommand << withInstall

            sh installCommand.join(' ')
        }

        if (withBehatServers) {
            sh "php -S 0.0.0.0:8000 -t ${WORKSPACE}/moodle &"
        }

        // Workaround for the withEnv below not appearing to work.
        envFile.text = envFile.text.replace('MOODLE_START_BEHAT_SERVERS=YES', '')

        // The script has a flag to prevent the servers starting but appears to override it with an environment
        // variable if the plugin has behat tests (in TestSuiteInstaller::getBehatInstallProcesses())
        withEnv(["MOODLE_START_BEHAT_SERVERS=false"]) {
            body()
        }

    }

    if (withBehatServers) {
        sh "docker stop ${buildTag}-selenium"
    }
    sh "docker network rm ${buildTag}"

    // TODO: Cleanup stuff should be in a finally block probably.
    // No prune is very important or all intermediate images will be removed on first build!
    sh "docker rmi --no-prune ${buildTag}"

    cleanWs deleteDirs: true, notFailBuild: true, patterns: [
        [pattern: ".composer/**", type: 'EXCLUDE'],
        [pattern: ".npm/**", type: 'EXCLUDE']
    ]

}