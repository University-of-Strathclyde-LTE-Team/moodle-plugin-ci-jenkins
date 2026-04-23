def call() {
    cleanWs deleteDirs: true, notFailBuild: true, patterns: [
        [pattern: ".composer/**", type: 'EXCLUDE'],
        [pattern: ".npm/**", type: 'EXCLUDE']
    ]
}
