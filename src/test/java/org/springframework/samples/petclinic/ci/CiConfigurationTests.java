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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiConfigurationTests {

	@Test
	void usesGradleAndJenkinsOnly() {
		assertAll(() -> assertTrue(Files.isRegularFile(Path.of("build.gradle"))),
				() -> assertTrue(Files.isRegularFile(Path.of("gradlew"))),
				() -> assertTrue(Files.isRegularFile(Path.of("gradlew.bat"))));

		List<Path> forbiddenPaths = List.of(Path.of("pom.xml"), Path.of("mvnw"), Path.of("mvnw.cmd"), Path.of(".mvn"),
				Path.of(".github", "workflows"));
		forbiddenPaths
			.forEach(path -> assertFalse(Files.exists(path), () -> "Remove non-Jenkins/Gradle path: " + path));
	}

	@Test
	void jenkinsPipelineBuildsTestsReportsAndPackagesWithGradle() throws IOException {
		Path jenkinsfile = Path.of("Jenkinsfile");
		assertTrue(Files.isRegularFile(jenkinsfile), "Jenkinsfile must exist");
		String pipeline = Files.readString(jenkinsfile);

		List<String> requiredFragments = List.of("githubPush()", "cron('H 14 * * *')", "stage('Checkout')",
				"stage('Verify Tools')", "stage('Scheduled Clean')", "triggeredBy 'TimerTrigger'", "stage('Compile')",
				"./gradlew compileJava --no-daemon", "stage('Test and Verify')", "./gradlew check --no-daemon",
				"stage('Package')", "./gradlew bootJar --no-daemon", "build/test-results/test/*.xml",
				"build/reports/tests/test", "build/libs/*.jar");
		long missing = requiredFragments.stream().filter(fragment -> !pipeline.contains(fragment)).count();
		assertEquals(0, missing, () -> "Missing " + missing + " required Jenkins Pipeline fragments");
	}

	@Test
	void provisionsJenkinsWithRequiredPluginsSecurityAndPipelineJob() throws IOException {
		Path dockerfile = Path.of("infra", "jenkins", "Dockerfile");
		Path pluginsFile = Path.of("infra", "jenkins", "plugins.txt");
		Path cascFile = Path.of("infra", "jenkins", "jenkins.yaml");
		assertTrue(Files.isRegularFile(dockerfile), "Jenkins Dockerfile must exist");
		assertTrue(Files.isRegularFile(pluginsFile), "Jenkins plugins.txt must exist");
		assertTrue(Files.isRegularFile(cascFile), "Jenkins JCasC file must exist");

		String plugins = Files.readString(pluginsFile);
		List<String> requiredPlugins = List.of("pipeline-model-definition", "workflow-basic-steps",
				"workflow-durable-task-step", "workflow-scm-step", "git", "github", "github-branch-source", "junit",
				"htmlpublisher", "pipeline-graph-view", "configuration-as-code", "job-dsl");
		assertTrue(requiredPlugins.stream().allMatch(plugins::contains),
				"Every reporting and provisioning plugin is required");

		String casc = Files.readString(cascFile);
		assertAll(() -> assertTrue(casc.contains("${JENKINS_ADMIN_ID}")),
				() -> assertTrue(casc.contains("${JENKINS_ADMIN_PASSWORD}")),
				() -> assertTrue(casc.contains("pipelineJob('petclinic-ci')")),
				() -> assertTrue(casc.contains("System.getenv('PETCLINIC_REPO_URL')")),
				() -> assertFalse(casc.contains("password: admin"), "Do not hard-code the Jenkins password"));
	}

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

	@Test
	void documentsTheCompleteJenkinsAndGradleDemo() throws IOException {
		Path guideFile = Path.of("docs", "DEMO_GUIDE.md");
		assertTrue(Files.isRegularFile(guideFile), "docs/DEMO_GUIDE.md must exist");
		String guide = Files.readString(guideFile);
		String readme = Files.readString(Path.of("README.md"));

		List<String> requiredGuideText = List.of("docker compose", "http://localhost:8081", "/github-webhook/",
				"Gradle Test Report", "Artifacts", "UP-TO-DATE",
				"docker compose -f docker-compose.jenkins.yml stop tunnel");
		assertAll(
				() -> assertTrue(requiredGuideText.stream().allMatch(guide::contains),
						"Guide must cover setup, webhook, Jenkins UI, incremental builds, and shutdown"),
				() -> assertTrue(readme.contains("Jenkins and Gradle CI Demo")),
				() -> assertTrue(readme.contains("docs/DEMO_GUIDE.md")));
	}

}
