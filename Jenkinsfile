pipeline {
  agent any

  environment {
    JAVA_HOME = '/opt/java17'
    PATH = "/opt/java17/bin:${env.PATH}"
  }

  options {
    disableConcurrentBuilds()
    skipDefaultCheckout(true)
    skipStagesAfterUnstable()
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
  }

  triggers {
    githubPush()
    pollSCM('* * * * *')
    cron('H 14 * * *')
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Verify Tools') {
      steps {
        sh 'java -version'
        sh './gradlew --version'
      }
    }

    stage('Scheduled Clean') {
      when {
        triggeredBy 'TimerTrigger'
      }
      steps {
        sh './gradlew clean --no-daemon'
      }
    }

    stage('Compile') {
      steps {
        sh './gradlew compileJava --no-daemon'
      }
    }

    stage('Test and Verify') {
      steps {
        sh './gradlew check --no-daemon'
      }
    }

    stage('Package') {
      steps {
        sh './gradlew bootJar --no-daemon'
      }
    }
  }

  post {
    always {
      junit testResults: 'build/test-results/test/*.xml', allowEmptyResults: true
      publishHTML target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: 'build/reports/tests/test',
        reportFiles: 'index.html',
        reportName: 'Gradle Test Report'
      ]
    }
    success {
      archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
    }
  }
}
