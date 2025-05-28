#!/usr/bin/env nextflow

nextflow.enable.dsl=2

process TEST_FAIL {
    executor 'dagger'
    container 'ubuntu:latest'
    
    output:
    stdout
    
    script:
    """
    echo "This command will fail"
    exit 42
    """
}

process TEST_SUCCESS {
    executor 'dagger'
    container 'ubuntu:latest'
    
    output:
    stdout
    
    script:
    """
    echo "This command will succeed"
    exit 0
    """
}

workflow {
    // This should fail with exit code 42
    TEST_FAIL()
    
    // This should succeed
    TEST_SUCCESS()
    TEST_SUCCESS.out.view()
} 