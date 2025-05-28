package adamrtalbot.plugin

import nextflow.processor.TaskProcessor
import nextflow.script.ProcessConfig
import nextflow.executor.Executor
import spock.lang.Specification

/**
 * Tests the container validation implemented by {@link NfDaggerObserver}
 */
class NfDaggerContainerValidationTest extends Specification {

    def 'should throw exception when dagger process missing container'() {
        given:
        def observer = new NfDaggerObserver()
        def executor = Mock(Executor) {
            getName() >> 'dagger'
        }
        def config = Mock(ProcessConfig) {
            getProperty('executor') >> 'dagger'
            getProperty('container') >> null
        }
        def process = Mock(TaskProcessor) {
            getExecutor() >> executor
            getConfig() >> config
            getName() >> 'foo'
            getExecutor() >> executor
        }

        when:
        observer.onProcessCreate(process)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('foo')
    }

    def 'should not throw when dagger process defines container'() {
        given:
        def observer = new NfDaggerObserver()
        def executor = Mock(Executor) {
            getName() >> 'dagger'
        }
        def config = Mock(ProcessConfig) {
            getProperty('executor') >> 'dagger'
            getProperty('container') >> 'ubuntu:latest'
        }
        def process = Mock(TaskProcessor) {
            getExecutor() >> executor
            getConfig() >> config
            getName() >> 'foo'
        }

        when:
        observer.onProcessCreate(process)

        then:
        noExceptionThrown()
    }

    def 'should ignore processes not using dagger executor'() {
        given:
        def observer = new NfDaggerObserver()
        def executor = Mock(Executor) {
            getName() >> 'local'
        }
        def config = Mock(ProcessConfig) {
            getProperty('executor') >> 'local'
            getProperty('container') >> null
        }
        def process = Mock(TaskProcessor) {
            getExecutor() >> executor
            getConfig() >> config
            getName() >> 'foo'
        }

        when:
        observer.onProcessCreate(process)

        then:
        noExceptionThrown()
    }
} 