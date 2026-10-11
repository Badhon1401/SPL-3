# CodePulse end-to-end API tests

## Where to put it
Copy `CodePulseE2ETest.java` to your backend project here (create the folders if missing):

    backend/src/test/java/com/codepulse/e2e/CodePulseE2ETest.java

Make sure `pom.xml` has the test starter (most Spring Boot projects already do):

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>

## How to run (from the backend folder, Windows PowerShell or any shell)
Run ONE test:

    mvn -Dtest=CodePulseE2ETest#tc03_login_success test

Run ALL tests:

    mvn -Dtest=CodePulseE2ETest test

Run all except the ones that call real AI providers / Codeforces:

    mvn -Dtest=CodePulseE2ETest -DexcludedGroups=ai,sync test

Run only the AI tests:

    mvn -Dtest=CodePulseE2ETest -Dgroups=ai test

(In IntelliJ / VS Code you can also click the green arrow next to a single method or the class.)

## Notes
* Each test is independent, so any single test can run alone.
* The tests start the real application on a random port and use your configured database.
  Each run creates users named `e2e_...@example.com`. To keep your real (Neon) data clean, point the
  tests at a separate database: create `src/test/resources/application-test.properties` with a
  different `spring.datasource.url` and run with `-Dspring.profiles.active=test`.
* `ai` tests need at least one AI key (Groq / Mistral / OpenRouter) in the properties.
* Field names (`username`, `email`, `password`, `fullName`, `codeforcesHandle`, `token`,
  `totalSubmissions`, `items`) follow the report and README; if a test fails on a name, check the DTO.
