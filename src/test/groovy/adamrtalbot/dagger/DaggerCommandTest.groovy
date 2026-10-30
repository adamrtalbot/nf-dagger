package adamrtalbot.dagger

import spock.lang.Specification

class DaggerCommandTest extends Specification {

    def 'should build Dagger 1.0 command with mounts'() {
        when:
        def cmd = DaggerTaskHandler.daggerCommand('ubuntu:latest', '/work/ab/cd', ['/work/ab/cd', '/my data'])

        then:
        cmd == ['dagger', '-m', 'core', 'api', 'call', 'container', 'from', '--address', 'ubuntu:latest',
                'with-mounted-directory', '--path', '/work/ab/cd', '--source', '/work/ab/cd',
                'with-mounted-directory', '--path', '/my data', '--source', '/my data',
                'with-workdir', '--path', '/work/ab/cd',
                'with-exec', '--args', 'bash,.command.run', '--expect', 'ANY',
                'directory', '--path', '/work/ab/cd',
                'export', '--path', '.']
    }

    def 'should require Dagger 1.0 or later'() {
        expect:
        DaggerExecutor.isSupportedVersion(output) == expected

        where:
        output                                                                          | expected
        'version:     v1.0.0-beta.21\ncommit:      a7a2e64e\nrunner-host: image://registry.dagger.io/engine:v1.0.0-beta.21' | true
        'dagger v2.3.0 (x) linux/amd64'                                                 | true
        'dagger v0.18.9 (docker-image://registry.dagger.io/engine:v0.18.9) darwin/arm64' | false
        'dagger v0.21.11 (image://registry.dagger.io/engine:v0.21.11) darwin/arm64/v8'  | false
        'command not found'                                                             | false
    }
}
