# PetClinic Jenkins and Gradle CI Demo Guide

## What the demo shows

This demo separates each tool's responsibility:

- GitHub stores the shared mainline and sends push events.
- Jenkins provides the CI web UI, pipeline graph, history, logs, reports, and artifacts.
- Gradle compiles, tests, performs quality checks, decides which tasks are up-to-date, and creates the executable Spring Boot JAR.

## Prerequisites

- Docker Desktop running with Linux containers
- Git
- JDK 17
- A public GitHub repository containing this project

## Configure local secrets

Copy `.env.example` to an untracked `.env`. Set `PETCLINIC_REPO_URL` to your exact public HTTPS clone URL and replace `JENKINS_ADMIN_PASSWORD` with a new demo-only password.

Confirm that Git protects the file:

```powershell
git check-ignore -v .env
```

## Start Jenkins and the tunnel

```powershell
docker compose --env-file .env -f docker-compose.jenkins.yml up --build -d
docker compose --env-file .env -f docker-compose.jenkins.yml ps
docker compose --env-file .env -f docker-compose.jenkins.yml logs tunnel
```

Copy the `https://...trycloudflare.com` URL from the tunnel log. Jenkins is available locally at <http://localhost:8081>.

## Configure the GitHub webhook

Open the public repository's **Settings → Webhooks → Add webhook** page.

- Payload URL: the tunnel URL followed by `/github-webhook/`
- Content type: `application/json`
- Events: **Just the push event**
- Active: enabled

Save the webhook and confirm that GitHub reports a successful delivery.

## Prime the Pipeline

Open **Jenkins → petclinic-ci → Build Now** once. This loads `Jenkinsfile` and registers the GitHub push trigger. Confirm this manual build before testing the webhook.

## Run the application demo

```powershell
.\gradlew.bat bootRun
```

Open <http://localhost:8080>. View veterinarians, find or create an owner, add a pet, add a visit, and reopen owner details. Stop the application with Ctrl+C after this part.

## Run the complete CI demo

1. Make a harmless visible change.
2. Commit and push it to `main`.
3. Show the successful delivery under GitHub **Settings → Webhooks**.
4. Open the automatically triggered `petclinic-ci` Jenkins build.
5. Show the Pipeline stages: Checkout, Verify Tools, Compile, Test and Verify, and Package.
6. Open **Test Result** to show JUnit results.
7. Open **Gradle Test Report** to show Gradle's HTML report.
8. Show the executable JAR under **Artifacts**.
9. Run **Build Now** again without changing source and find Gradle tasks marked `UP-TO-DATE` in the console.
10. Open `Jenkinsfile` and explain that `cron('H 14 * * *')` performs the daily build; timer builds also run Scheduled Clean.

## Jenkins UI results

The tool demo is not terminal-only. Jenkins displays:

- green/red build status and history;
- a Pipeline stage graph;
- the Git commit and build cause;
- JUnit counts, failures, and trends;
- the browsable Gradle Test Report;
- stage console output;
- a downloadable, fingerprinted JAR.

## Troubleshooting

- **No automatic build:** redeliver the GitHub webhook and confirm its URL ends with `/github-webhook/`.
- **Tunnel URL changed:** replace the GitHub Payload URL and redeliver.
- **Jenkins still starting:** run `docker compose --env-file .env -f docker-compose.jenkins.yml ps` until Jenkins is healthy.
- **Gradle download failed:** confirm that the Jenkins container has internet access, then select Build Now again.
- **Port conflict:** keep PetClinic on 8080 and Jenkins on 8081; stop any conflicting service before the demo.

## Shut down safely

Stop public access immediately after the demo:

```powershell
docker compose -f docker-compose.jenkins.yml stop tunnel
```

Stop Jenkins while preserving its build history:

```powershell
docker compose -f docker-compose.jenkins.yml stop jenkins
```

`docker compose -f docker-compose.jenkins.yml down` removes containers and the network. Do not add `--volumes` unless Jenkins history should also be deleted.
