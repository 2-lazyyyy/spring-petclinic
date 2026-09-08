# PetClinic Jenkins and Gradle CI Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a repeatable classroom demo in which a push to a public PetClinic GitHub repository triggers Docker-hosted Jenkins, Jenkins runs the Gradle Wrapper, publishes UI-visible test reports, and archives the executable Spring Boot JAR.

**Architecture:** Keep the official single-module Spring Boot PetClinic application and H2 data store, remove Maven and GitHub Actions from our copy, and add a repository-root Jenkins Pipeline. Docker Compose runs a JDK 17 Jenkins controller and a temporary Cloudflare tunnel; Jenkins Configuration as Code and Job DSL create the secured controller and Pipeline job from environment-supplied values.

**Tech Stack:** Java 17, Spring Boot 4.1, Gradle Wrapper 9.5.1, JUnit 5, Jenkins LTS JDK 17, Jenkins Pipeline/JUnit/HTML Publisher/Pipeline Graph View/JCasC/Job DSL plugins, Docker Compose, Cloudflare Quick Tunnel, Git, public GitHub repository, H2.

**Spec:** `docs/superpowers/specs/2026-09-08-petclinic-jenkins-gradle-ci-design.md`

## Global Constraints

- Keep the application as the veterinary-domain PetClinic; do not convert it into a human clinic system.
- Use Java 17 and the checked-in Gradle Wrapper for every application build.
- Use Jenkins as the only CI server and Gradle as the only build tool in our copy.
- Use the default H2 in-memory database; do not add MySQL, PostgreSQL, Kubernetes, or production deployment.
- Expose PetClinic at `http://localhost:8080` and Jenkins at `http://localhost:8081`.
- Store the project in a public, user-owned GitHub repository and trigger Jenkins through `/github-webhook/` on a temporary Cloudflare URL.
- Publish JUnit results, the Gradle HTML test report, build history, stage logs, and a fingerprinted boot JAR in Jenkins.
- Preserve `LICENSE.txt` and upstream Apache License 2.0 attribution.
- Never commit `.env`, GitHub credentials, Jenkins credentials, or tunnel secrets.
- Stop the temporary public tunnel after the demo.

---

## File map

| File | Responsibility |
|---|---|
| `src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java` | Test-first contract for the build-tool policy, Pipeline, Docker/Jenkins files, secrets policy, and documentation. |
| `Jenkinsfile` | Defines push and daily triggers, visible Pipeline stages, Gradle tasks, reports, and artifact archival. |
| `infra/jenkins/Dockerfile` | Builds the JDK 17 Jenkins image and installs the selected plugins. |
| `infra/jenkins/plugins.txt` | Declares Jenkins plugins installed at image-build time. |
| `infra/jenkins/jenkins.yaml` | Configures Jenkins security, location, executors, and the `petclinic-ci` Pipeline job. |
| `docker-compose.jenkins.yml` | Runs persistent Jenkins and the temporary Cloudflare tunnel on an isolated network. |
| `.env.example` | Provides non-secret, runnable example values and documents required environment keys. |
| `.gitignore` | Prevents the real `.env` from being committed and removes obsolete Maven exceptions. |
| `docs/DEMO_GUIDE.md` | Gives exact setup, GitHub, webhook, UI presentation, troubleshooting, and shutdown steps. |
| `README.md` | Identifies our Jenkins+Gradle demo and links to the detailed guide while preserving upstream attribution. |
| `.github/workflows/*`, `.mvn/**`, `pom.xml`, `mvnw`, `mvnw.cmd` | Removed so our copy has one CI server and one build tool. |

---

### Task 1: Enforce the Gradle-only and Jenkins-only project boundary

**Files:**
- Create: `src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java`
- Delete: `pom.xml`
- Delete: `mvnw`
- Delete: `mvnw.cmd`
- Delete: `.mvn/wrapper/maven-wrapper.properties`
- Delete: `.mvn/wrapper/maven-wrapper.jar`
- Delete: `.github/workflows/deploy-and-test-cluster.yml`
- Delete: `.github/workflows/gradle-build.yml`
- Delete: `.github/workflows/maven-build.yml`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: the upstream PetClinic source tree and Gradle Wrapper.
- Produces: a repository in which `build.gradle`, `gradlew`, and `gradlew.bat` are present while Maven entry points and GitHub Actions workflows are absent.

- [ ] **Step 1: Write the failing build-tool policy test**

Create `CiConfigurationTests.java` with this content:

```java
/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.ci;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiConfigurationTests {

	@Test
	void usesGradleAndJenkinsOnly() {
		assertAll(
				() -> assertTrue(Files.isRegularFile(Path.of("build.gradle"))),
				() -> assertTrue(Files.isRegularFile(Path.of("gradlew"))),
				() -> assertTrue(Files.isRegularFile(Path.of("gradlew.bat"))));

		List<Path> forbiddenPaths = List.of(Path.of("pom.xml"), Path.of("mvnw"), Path.of("mvnw.cmd"),
				Path.of(".mvn"), Path.of(".github", "workflows"));
		forbiddenPaths.forEach(path -> assertFalse(Files.exists(path), () -> "Remove non-Jenkins/Gradle path: " + path));
	}

}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests org.springframework.samples.petclinic.ci.CiConfigurationTests.usesGradleAndJenkinsOnly --no-daemon
```

Expected: FAIL with one or more `Remove non-Jenkins/Gradle path` assertions because Maven and `.github/workflows` still exist.

- [ ] **Step 3: Remove the alternative build and CI files**

Use `apply_patch` to delete every file listed under **Delete** above. Remove the now-empty `.mvn/wrapper`, `.mvn`, and `.github/workflows` directories only after verifying their resolved paths remain under the PetClinic repository. Preserve `.github/dco.yml`, because it is contribution metadata rather than a CI workflow.

- [ ] **Step 4: Remove the obsolete Maven wrapper exception from `.gitignore`**

Delete only this line:

```gitignore
!.mvn/wrapper/maven-wrapper.jar
```

Do not alter the Gradle ignore rules.

- [ ] **Step 5: Re-run the focused test and verify GREEN**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 6: Run the upstream test suite to detect regression**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

Expected: `BUILD SUCCESSFUL` and all existing PetClinic tests pass.

- [ ] **Step 7: Commit the build policy**

```powershell
git add .gitignore src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java
git add -u .github/workflows .mvn pom.xml mvnw mvnw.cmd
git commit -m "build: standardize on Jenkins and Gradle"
```

---

### Task 2: Add the Jenkins Pipeline contract and implementation

**Files:**
- Modify: `src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java`
- Create: `Jenkinsfile`

**Interfaces:**
- Consumes: Gradle tasks `compileJava`, `check`, `clean`, and `bootJar` from `build.gradle`.
- Produces: Jenkins stages named `Checkout`, `Verify Tools`, `Scheduled Clean`, `Compile`, `Test and Verify`, and `Package`; JUnit XML input `build/test-results/test/*.xml`; HTML input `build/reports/tests/test/index.html`; boot JAR input `build/libs/*.jar`.

- [ ] **Step 1: Add the failing Pipeline contract test**

Add imports for `IOException` and `assertEquals`, then add this method inside `CiConfigurationTests`:

```java
	@Test
	void jenkinsPipelineBuildsTestsReportsAndPackagesWithGradle() throws IOException {
		Path jenkinsfile = Path.of("Jenkinsfile");
		assertTrue(Files.isRegularFile(jenkinsfile), "Jenkinsfile must exist");
		String pipeline = Files.readString(jenkinsfile);

		List<String> requiredFragments = List.of("githubPush()", "cron('H 14 * * *')", "stage('Checkout')",
				"stage('Verify Tools')", "stage('Scheduled Clean')", "triggeredBy 'TimerTrigger'",
				"stage('Compile')", "./gradlew compileJava --no-daemon", "stage('Test and Verify')",
				"./gradlew check --no-daemon", "stage('Package')", "./gradlew bootJar --no-daemon",
				"build/test-results/test/*.xml", "build/reports/tests/test", "build/libs/*.jar");
		long missing = requiredFragments.stream().filter(fragment -> !pipeline.contains(fragment)).count();
		assertEquals(0, missing, () -> "Missing " + missing + " required Jenkins Pipeline fragments");
	}
```

Ensure the complete static imports include:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
```

- [ ] **Step 2: Run the Pipeline contract test and verify RED**

```powershell
.\gradlew.bat test --tests org.springframework.samples.petclinic.ci.CiConfigurationTests.jenkinsPipelineBuildsTestsReportsAndPackagesWithGradle --no-daemon
```

Expected: FAIL with `Jenkinsfile must exist`.

- [ ] **Step 3: Create the minimal `Jenkinsfile`**

```groovy
pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    skipDefaultCheckout(true)
    skipStagesAfterUnstable()
    buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
  }

  triggers {
    githubPush()
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
```

- [ ] **Step 4: Run the contract test and verify GREEN**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 5: Run the Gradle commands Jenkins will execute**

```powershell
.\gradlew.bat compileJava check bootJar --no-daemon
```

Expected: `BUILD SUCCESSFUL`, XML files under `build/test-results/test`, HTML under `build/reports/tests/test`, and a JAR under `build/libs`.

- [ ] **Step 6: Commit the Pipeline**

```powershell
git add Jenkinsfile src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java
git commit -m "ci: add Jenkins Gradle pipeline"
```

---

### Task 3: Provision Jenkins reproducibly

**Files:**
- Modify: `src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java`
- Create: `infra/jenkins/Dockerfile`
- Create: `infra/jenkins/plugins.txt`
- Create: `infra/jenkins/jenkins.yaml`

**Interfaces:**
- Consumes: `JENKINS_ADMIN_ID`, `JENKINS_ADMIN_PASSWORD`, and `PETCLINIC_REPO_URL` environment variables.
- Produces: image `petclinic-jenkins:lts-jdk17`, secured Jenkins configuration, and Pipeline job `petclinic-ci` loading `Jenkinsfile` from `*/main`.

- [ ] **Step 1: Add the failing Jenkins provisioning contract**

Add this method inside `CiConfigurationTests`:

```java
	@Test
	void provisionsJenkinsWithRequiredPluginsSecurityAndPipelineJob() throws IOException {
		Path dockerfile = Path.of("infra", "jenkins", "Dockerfile");
		Path pluginsFile = Path.of("infra", "jenkins", "plugins.txt");
		Path cascFile = Path.of("infra", "jenkins", "jenkins.yaml");
		assertTrue(Files.isRegularFile(dockerfile), "Jenkins Dockerfile must exist");
		assertTrue(Files.isRegularFile(pluginsFile), "Jenkins plugins.txt must exist");
		assertTrue(Files.isRegularFile(cascFile), "Jenkins JCasC file must exist");

		String plugins = Files.readString(pluginsFile);
		List<String> requiredPlugins = List.of("workflow-aggregator", "git", "github", "github-branch-source",
				"junit", "htmlpublisher", "pipeline-graph-view", "configuration-as-code", "job-dsl");
		assertTrue(requiredPlugins.stream().allMatch(plugins::contains), "Every reporting and provisioning plugin is required");

		String casc = Files.readString(cascFile);
		assertAll(() -> assertTrue(casc.contains("${JENKINS_ADMIN_ID}")),
				() -> assertTrue(casc.contains("${JENKINS_ADMIN_PASSWORD}")),
				() -> assertTrue(casc.contains("pipelineJob('petclinic-ci')")),
				() -> assertTrue(casc.contains("System.getenv('PETCLINIC_REPO_URL')")),
				() -> assertFalse(casc.contains("password: admin"), "Do not hard-code the Jenkins password"));
	}
```

- [ ] **Step 2: Run the provisioning contract and verify RED**

```powershell
.\gradlew.bat test --tests org.springframework.samples.petclinic.ci.CiConfigurationTests.provisionsJenkinsWithRequiredPluginsSecurityAndPipelineJob --no-daemon
```

Expected: FAIL because the three `infra/jenkins` files do not exist.

- [ ] **Step 3: Create `infra/jenkins/plugins.txt`**

```text
configuration-as-code
workflow-aggregator
git
github
github-branch-source
junit
htmlpublisher
pipeline-graph-view
job-dsl
```

- [ ] **Step 4: Create `infra/jenkins/Dockerfile`**

```dockerfile
FROM jenkins/jenkins:lts-jdk17

USER root
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl git \
    && rm -rf /var/lib/apt/lists/*

USER jenkins
COPY --chown=jenkins:jenkins infra/jenkins/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
```

- [ ] **Step 5: Create `infra/jenkins/jenkins.yaml`**

```yaml
jenkins:
  systemMessage: "PetClinic Jenkins + Gradle CI Demo\n"
  numExecutors: 2
  mode: NORMAL
  slaveAgentPort: 50000
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: "${JENKINS_ADMIN_ID}"
          password: "${JENKINS_ADMIN_PASSWORD}"
  authorizationStrategy:
    loggedInUsersCanDoAnything:
      allowAnonymousRead: false
  crumbIssuer:
    standard:
      excludeClientIPFromCrumb: true
  remotingSecurity:
    enabled: true

unclassified:
  location:
    url: "http://localhost:8081/"

jobs:
  - script: >
      pipelineJob('petclinic-ci') {
        description('Build, test, report, and package PetClinic with the Gradle Wrapper.')
        logRotator {
          numToKeep(20)
          artifactNumToKeep(10)
        }
        definition {
          cpsScm {
            scm {
              git {
                remote {
                  url(System.getenv('PETCLINIC_REPO_URL'))
                }
                branch('*/main')
              }
            }
            scriptPath('Jenkinsfile')
            lightweight()
          }
        }
      }
```

- [ ] **Step 6: Run the provisioning contract and verify GREEN**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 7: Build the custom Jenkins image**

```powershell
docker build --file infra/jenkins/Dockerfile --tag petclinic-jenkins:lts-jdk17 .
```

Expected: image build succeeds and `jenkins-plugin-cli` resolves all selected plugins.

- [ ] **Step 8: Commit provisioning**

```powershell
git add infra/jenkins src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java
git commit -m "ci: provision Jenkins with configuration as code"
```

---

### Task 4: Add Docker Compose, the tunnel, and secret-safe configuration

**Files:**
- Modify: `src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java`
- Create: `docker-compose.jenkins.yml`
- Create: `.env.example`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: a real untracked `.env` containing the three keys documented by `.env.example`.
- Produces: Jenkins on host port 8081, agent port 50000, persistent volume `jenkins_home`, and a Cloudflare Quick Tunnel targeting `http://jenkins:8080`.

- [ ] **Step 1: Add the failing Compose and secrets contract**

Add this method inside `CiConfigurationTests`:

```java
	@Test
	void composeRunsJenkinsAndTemporaryTunnelWithoutTrackedSecrets() throws IOException {
		Path composeFile = Path.of("docker-compose.jenkins.yml");
		Path envExample = Path.of(".env.example");
		assertTrue(Files.isRegularFile(composeFile), "Jenkins Compose file must exist");
		assertTrue(Files.isRegularFile(envExample), ".env.example must exist");

		String compose = Files.readString(composeFile);
		String env = Files.readString(envExample);
		String ignore = Files.readString(Path.of(".gitignore"));
		assertAll(() -> assertTrue(compose.contains("8081:8080")), () -> assertTrue(compose.contains("50000:50000")),
				() -> assertTrue(compose.contains("cloudflare/cloudflared")),
				() -> assertTrue(compose.contains("http://jenkins:8080")),
				() -> assertTrue(compose.contains("jenkins_home:/var/jenkins_home")),
				() -> assertTrue(env.contains("JENKINS_ADMIN_ID=")),
				() -> assertTrue(env.contains("JENKINS_ADMIN_PASSWORD=")),
				() -> assertTrue(env.contains("PETCLINIC_REPO_URL=")),
				() -> assertTrue(ignore.lines().anyMatch(".env"::equals), ".env must be ignored"));
	}
```

- [ ] **Step 2: Run the Compose contract and verify RED**

```powershell
.\gradlew.bat test --tests org.springframework.samples.petclinic.ci.CiConfigurationTests.composeRunsJenkinsAndTemporaryTunnelWithoutTrackedSecrets --no-daemon
```

Expected: FAIL because `docker-compose.jenkins.yml` and `.env.example` do not exist.

- [ ] **Step 3: Create `.env.example` with valid non-secret defaults**

```dotenv
JENKINS_ADMIN_ID=admin
JENKINS_ADMIN_PASSWORD=local-demo-only-change-me
PETCLINIC_REPO_URL=https://github.com/spring-projects/spring-petclinic.git
```

The example repository URL is intentionally valid so Compose can be validated before the user-owned repository exists. The real `.env` must use the user-owned repository URL and a separately generated password.

- [ ] **Step 4: Add the exact `.env` ignore rule**

Add this block near the top of `.gitignore`:

```gitignore
### Local Jenkins demo secrets ###
.env
```

- [ ] **Step 5: Create `docker-compose.jenkins.yml`**

```yaml
services:
  jenkins:
    build:
      context: .
      dockerfile: infra/jenkins/Dockerfile
    image: petclinic-jenkins:lts-jdk17
    container_name: petclinic-jenkins
    restart: unless-stopped
    ports:
      - "8081:8080"
      - "50000:50000"
    environment:
      JAVA_OPTS: "-Djenkins.install.runSetupWizard=false"
      CASC_JENKINS_CONFIG: "/var/jenkins_home/casc_configs/jenkins.yaml"
      JENKINS_ADMIN_ID: "${JENKINS_ADMIN_ID}"
      JENKINS_ADMIN_PASSWORD: "${JENKINS_ADMIN_PASSWORD}"
      PETCLINIC_REPO_URL: "${PETCLINIC_REPO_URL}"
    volumes:
      - jenkins_home:/var/jenkins_home
      - ./infra/jenkins/jenkins.yaml:/var/jenkins_home/casc_configs/jenkins.yaml:ro
    healthcheck:
      test: ["CMD-SHELL", "curl --fail --silent http://localhost:8080/login > /dev/null"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 30s
    networks:
      - petclinic_ci

  tunnel:
    image: cloudflare/cloudflared:latest
    container_name: petclinic-jenkins-tunnel
    restart: "no"
    command: tunnel --no-autoupdate --url http://jenkins:8080
    depends_on:
      jenkins:
        condition: service_healthy
    networks:
      - petclinic_ci

volumes:
  jenkins_home:

networks:
  petclinic_ci:
    driver: bridge
```

- [ ] **Step 6: Run the contract and verify GREEN**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 7: Validate the Compose model without starting containers**

```powershell
docker compose --env-file .env.example -f docker-compose.jenkins.yml config
```

Expected: exit code 0; services `jenkins` and `tunnel`, network `petclinic_ci`, and volume `jenkins_home` are present.

- [ ] **Step 8: Prove `.env` cannot be tracked accidentally**

Create a temporary `.env` copy, then run:

```powershell
git check-ignore -v .env
```

Expected: output identifies the `.env` rule in `.gitignore`. Remove the temporary copy if it contains only example values; a real `.env` is created later.

- [ ] **Step 9: Commit Compose infrastructure**

```powershell
git add docker-compose.jenkins.yml .env.example .gitignore src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java
git commit -m "ci: run Jenkins and webhook tunnel with Docker Compose"
```

---

### Task 5: Write the repeatable demo and UI guide

**Files:**
- Modify: `src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java`
- Create: `docs/DEMO_GUIDE.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: commands and file names from Tasks 1–4.
- Produces: one setup-to-shutdown presentation path with Jenkins UI locations and recovery steps.

- [ ] **Step 1: Add the failing documentation contract**

Add this method inside `CiConfigurationTests`:

```java
	@Test
	void documentsTheCompleteJenkinsAndGradleDemo() throws IOException {
		Path guideFile = Path.of("docs", "DEMO_GUIDE.md");
		assertTrue(Files.isRegularFile(guideFile), "docs/DEMO_GUIDE.md must exist");
		String guide = Files.readString(guideFile);
		String readme = Files.readString(Path.of("README.md"));

		List<String> requiredGuideText = List.of("docker compose", "http://localhost:8081", "/github-webhook/",
				"Gradle Test Report", "Artifacts", "UP-TO-DATE", "docker compose -f docker-compose.jenkins.yml stop tunnel");
		assertAll(() -> assertTrue(requiredGuideText.stream().allMatch(guide::contains),
				"Guide must cover setup, webhook, Jenkins UI, incremental builds, and shutdown"),
				() -> assertTrue(readme.contains("Jenkins and Gradle CI Demo")),
				() -> assertTrue(readme.contains("docs/DEMO_GUIDE.md")));
	}
```

- [ ] **Step 2: Run the documentation contract and verify RED**

```powershell
.\gradlew.bat test --tests org.springframework.samples.petclinic.ci.CiConfigurationTests.documentsTheCompleteJenkinsAndGradleDemo --no-daemon
```

Expected: FAIL with `docs/DEMO_GUIDE.md must exist`.

- [ ] **Step 3: Create `docs/DEMO_GUIDE.md`**

Write these sections with the exact commands shown:

```markdown
# PetClinic Jenkins and Gradle CI Demo Guide

## Prerequisites

- Docker Desktop running with Linux containers
- Git
- JDK 17
- A public GitHub repository containing this project

## Configure local secrets

Create an untracked `.env` from `.env.example`. Set `PETCLINIC_REPO_URL` to the exact HTTPS clone URL returned by GitHub and replace `JENKINS_ADMIN_PASSWORD` with a new demo-only password. Confirm protection with `git check-ignore -v .env`.

## Start Jenkins and the tunnel

```powershell
docker compose --env-file .env -f docker-compose.jenkins.yml up --build -d
docker compose --env-file .env -f docker-compose.jenkins.yml ps
docker compose --env-file .env -f docker-compose.jenkins.yml logs tunnel
```

Copy the `https://...trycloudflare.com` URL from the tunnel log. Jenkins is available locally at http://localhost:8081.

## Configure GitHub webhook

Open the public repository's **Settings → Webhooks → Add webhook** page. Set Payload URL to the tunnel URL followed by `/github-webhook/`, choose `application/json`, select **Just the push event**, activate the webhook, and save it.

## Prime the Pipeline

Open **Jenkins → petclinic-ci → Build Now** once. This loads `Jenkinsfile` and registers its GitHub push trigger. Confirm the manual build passes before testing the webhook.

## Run the application demo

```powershell
.\gradlew.bat bootRun
```

Open http://localhost:8080. View veterinarians, find or create an owner, add a pet, and add a visit. Stop the application with Ctrl+C after this part.

## Run the CI demo

Make a harmless visible change, then commit and push it. Open the GitHub webhook delivery and Jenkins **petclinic-ci** build. Show the Pipeline graph stages, **Test Result**, **Gradle Test Report**, console log, and **Artifacts** JAR.

Run **Build Now** again without changing source and open its console log. Point out Gradle tasks marked `UP-TO-DATE`. Show `cron('H 14 * * *')` and explain that timer builds run `clean` before verification.

## Troubleshooting

- No automatic build: redeliver the GitHub webhook and confirm the URL ends with `/github-webhook/`.
- Tunnel URL changed: replace the Payload URL in GitHub and redeliver.
- Jenkins still starting: run `docker compose --env-file .env -f docker-compose.jenkins.yml ps` until Jenkins is healthy.
- Gradle download failed: confirm the Jenkins container has internet access and run Build Now again.
- Port conflict: keep PetClinic on 8080 and Jenkins on 8081; stop the conflicting local service before the demo.

## Shut down safely

Stop public access immediately after the demo:

```powershell
docker compose -f docker-compose.jenkins.yml stop tunnel
```

Stop the remaining demo service while preserving Jenkins history:

```powershell
docker compose -f docker-compose.jenkins.yml stop jenkins
```

Use `docker compose -f docker-compose.jenkins.yml down` only when the containers and network should be removed. Do not add `--volumes` unless Jenkins history is intentionally being discarded.
```

- [ ] **Step 4: Update the top of `README.md`**

Replace the existing heading and build badges with:

```markdown
# Spring PetClinic — Jenkins and Gradle CI Demo

This repository is our classroom CI demonstration based on the official Spring PetClinic sample. The application remains licensed under Apache License 2.0; our copy uses the Gradle Wrapper as its only build entry point and Jenkins as its CI server.

For the complete Docker, Jenkins UI, GitHub webhook, Gradle report, and presentation workflow, read [`docs/DEMO_GUIDE.md`](docs/DEMO_GUIDE.md).
```

Replace the local-run introduction and commands so the only build-tool commands shown are:

```bash
./gradlew bootRun
./gradlew check bootJar
```

Delete Maven-specific run, container-image, CSS compilation, Eclipse import, and IntelliJ generation instructions. Keep the existing domain explanation, database profiles, source navigation, open-source interaction history, contribution text, and license section. State that the pre-generated CSS is used unchanged in this demo.

- [ ] **Step 5: Run the documentation contract and verify GREEN**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 6: Check that user-facing docs no longer advertise Maven or GitHub Actions**

```powershell
rg -n "mvnw|Maven|actions/workflows" README.md docs/DEMO_GUIDE.md
```

Expected: no matches.

- [ ] **Step 7: Run all tests and commit documentation**

```powershell
.\gradlew.bat test --no-daemon
git add README.md docs/DEMO_GUIDE.md src/test/java/org/springframework/samples/petclinic/ci/CiConfigurationTests.java
git commit -m "docs: add complete Jenkins Gradle demo guide"
```

Expected: tests pass before the commit succeeds.

---

### Task 6: Create the public GitHub mainline and local Jenkins environment

**Files:**
- Create locally but never track: `.env`
- Modify Git configuration only: add `origin`; retain `upstream`.

**Interfaces:**
- Consumes: signed-in GitHub browser session, local Git history, `.env.example`.
- Produces: public user-owned repository named `petclinic`, HTTPS `origin`, pushed `main`, and secret-safe local Jenkins configuration.

- [ ] **Step 1: Confirm local history and remote safety**

```powershell
git status --short
git remote -v
git log -5 --oneline
```

Expected: clean worktree; official URL is named `upstream`; no `origin` exists yet.

- [ ] **Step 2: Create the public repository**

In the signed-in GitHub UI, create a public repository named `petclinic`. Do not initialize it with README, `.gitignore`, or license because the local repository already contains all three. Record the HTTPS clone URL returned by GitHub.

- [ ] **Step 3: Add and verify the user-owned remote**

Set a PowerShell variable from the exact URL returned in Step 2, then run:

```powershell
$petclinicRepoUrl = Read-Host 'Paste the new public repository HTTPS clone URL'
git remote add origin $petclinicRepoUrl
git remote -v
```

Expected: `origin` points to the user-owned repository and `upstream` still points to `spring-projects/spring-petclinic`.

- [ ] **Step 4: Push the mainline**

```powershell
git push -u origin main
```

Expected: GitHub displays this repository and its commits on `main`.

- [ ] **Step 5: Create the real untracked `.env`**

Generate a temporary administrator password without printing it, obtain the exact repository URL with `git remote get-url origin`, and create `.env` with `apply_patch`. The file must contain exactly three assignments: `JENKINS_ADMIN_ID` set to `admin`, `JENKINS_ADMIN_PASSWORD` set to the generated value, and `PETCLINIC_REPO_URL` set to the command's exact output. Do not place explanatory or example text on the right-hand side of any assignment.

Run:

```powershell
git check-ignore -v .env
git status --short
```

Expected: `.env` is ignored and the worktree remains clean.

- [ ] **Step 6: Validate and start Jenkins before the public tunnel**

```powershell
docker compose --env-file .env -f docker-compose.jenkins.yml config
docker compose --env-file .env -f docker-compose.jenkins.yml up --build -d jenkins
docker compose --env-file .env -f docker-compose.jenkins.yml ps
```

Expected: `petclinic-jenkins` becomes healthy and `http://localhost:8081/login` responds.

- [ ] **Step 7: Log in and prime the Pipeline**

Open `http://localhost:8081`, log in using `.env`, open `petclinic-ci`, and select **Build Now**. Confirm the Pipeline loads the pushed `main` branch and completes all stages before exposing Jenkins publicly.

- [ ] **Step 8: Confirm UI outputs from the priming build**

Verify the build page provides:

- a successful Pipeline graph;
- test counts and test detail pages;
- the **Gradle Test Report** link;
- one JAR under **Artifacts** with a fingerprint;
- console lines showing Java 17 and Gradle 9.5.1.

Do not continue if any item is missing; correct the relevant repository configuration, repeat all automated checks, commit, push, and prime again.

---

### Task 7: Connect and prove the GitHub push webhook

**Files:**
- Modify: `docs/DEMO_GUIDE.md` only if verified UI wording or commands differ from the running system.

**Interfaces:**
- Consumes: healthy Jenkins, public GitHub repository, primed `petclinic-ci` job.
- Produces: verified GitHub push delivery, automatic Jenkins run, and a rehearsed classroom workflow.

- [ ] **Step 1: Start the temporary tunnel and capture its URL**

```powershell
docker compose --env-file .env -f docker-compose.jenkins.yml up -d tunnel
docker compose --env-file .env -f docker-compose.jenkins.yml logs tunnel
```

Expected: logs include one `https://` URL ending in `trycloudflare.com`. Treat that URL as temporary public access to Jenkins.

- [ ] **Step 2: Configure the GitHub webhook**

In GitHub open **Settings → Webhooks → Add webhook**. Use the exact tunnel URL followed by `/github-webhook/`, content type `application/json`, **Just the push event**, and Active enabled. Save it and confirm the initial delivery returns a successful HTTP status.

- [ ] **Step 3: Create a harmless documentation change for the trigger proof**

Use `apply_patch` to add one dated line under the README's Jenkins demo introduction:

```markdown
Webhook verification: GitHub push events automatically start the `petclinic-ci` Jenkins Pipeline.
```

- [ ] **Step 4: Commit and push the trigger proof**

```powershell
git add README.md
git commit -m "docs: verify GitHub webhook trigger"
git push origin main
```

Expected: the push finishes successfully.

- [ ] **Step 5: Verify the automatic run without pressing Build Now**

Confirm all of the following:

- GitHub shows a successful push webhook delivery.
- Jenkins creates a new `petclinic-ci` build whose cause is a GitHub push.
- Jenkins checks out the commit created in Step 4.
- All Pipeline stages pass.
- JUnit results, **Gradle Test Report**, and the fingerprinted JAR are present.

- [ ] **Step 6: Demonstrate Gradle incremental behavior**

Select **Build Now** without changing source. Open the console output and verify one or more eligible Gradle tasks are marked `UP-TO-DATE`. Explain that the timer-triggered build runs the separate `Scheduled Clean` stage, so its verification begins from a clean build directory.

- [ ] **Step 7: Run the built application and rehearse the user flow**

On the host run:

```powershell
.\gradlew.bat bootRun
```

Open `http://localhost:8080` and verify this exact flow: view veterinarians; search for an owner; create an owner if needed; add a pet; add a visit; reopen owner details and confirm the pet and visit history. Stop `bootRun` with Ctrl+C.

- [ ] **Step 8: Verify repository and test state before handoff**

```powershell
.\gradlew.bat check bootJar --no-daemon
git status --short
git log -7 --oneline
```

Expected: `BUILD SUCCESSFUL`, a clean worktree, and the implementation commits are visible.

- [ ] **Step 9: Stop public exposure**

```powershell
docker compose -f docker-compose.jenkins.yml stop tunnel
```

Expected: `petclinic-jenkins-tunnel` is stopped. Jenkins may remain running locally for the classroom demo; stop it with `docker compose -f docker-compose.jenkins.yml stop jenkins` when finished.

- [ ] **Step 10: Record only verified documentation corrections**

If the running Jenkins UI or Docker command output uses wording different from `docs/DEMO_GUIDE.md`, update only those verified details with `apply_patch`, run `CiConfigurationTests`, commit with `docs: align demo guide with verified workflow`, and push. If the guide already matches, make no additional commit.

---

## Final verification checklist

- [ ] `git status --short` is empty.
- [ ] `.\gradlew.bat check bootJar --no-daemon` reports `BUILD SUCCESSFUL`.
- [ ] `docker compose --env-file .env -f docker-compose.jenkins.yml config` succeeds.
- [ ] Jenkins is healthy at `http://localhost:8081`.
- [ ] A GitHub push automatically starts `petclinic-ci`.
- [ ] Pipeline graph shows every declared stage.
- [ ] JUnit details and **Gradle Test Report** are visible in Jenkins.
- [ ] The boot JAR is downloadable and fingerprinted.
- [ ] An unchanged manual build shows eligible `UP-TO-DATE` tasks.
- [ ] PetClinic works at `http://localhost:8080` with H2.
- [ ] `git check-ignore -v .env` proves the real secrets file is ignored.
- [ ] The Cloudflare tunnel is stopped when public webhook testing ends.
