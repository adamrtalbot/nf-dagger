#!/usr/bin/env nextflow

nextflow.enable.dsl=2

process HELLO_DAGGER {
    executor 'dagger'
    container 'ubuntu:latest'
    
    output:
    stdout
    
    script:
    """
    echo "Hello from Dagger executor!"
    echo "Running in container: \$(cat /etc/os-release | grep PRETTY_NAME)"
    echo "Current directory: \$(pwd)"
    echo "Files in directory: \$(ls -la)"
    """
}

workflow {
    HELLO_DAGGER()
    HELLO_DAGGER.out.view()
} 