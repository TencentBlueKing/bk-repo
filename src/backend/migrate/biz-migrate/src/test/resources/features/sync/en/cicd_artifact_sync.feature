@migrate
Feature: CicdArtifactSyncStepDefinitions

  Scenario: cicd send request to bkrepo migrate
    When cicd send request
    Then cicd receive