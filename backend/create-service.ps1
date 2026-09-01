[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ServiceName,

    [Parameter(Mandatory = $true)]
    [string]$PackageSuffix,

    [Parameter(Mandatory = $true)]
    [int]$Port
)

$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$modulePath = Join-Path $backendRoot $ServiceName
$javaBasePath = Join-Path $modulePath "src\main\java\com\fleet\$PackageSuffix"
$resourcesPath = Join-Path $modulePath "src\main\resources"
$configRepoPath = Join-Path $backendRoot "config-repo"
$parentPomPath = Join-Path $backendRoot "pom.xml"
$configFilePath = Join-Path $configRepoPath "$ServiceName.yml"

if (-not (Test-Path $parentPomPath)) {
    throw "Parent pom.xml not found in backend folder."
}

if (-not (Test-Path $configRepoPath)) {
    throw "config-repo folder not found."
}

if (Test-Path $modulePath) {
    throw "Module '$ServiceName' already exists."
}

function Convert-ToPascalCase([string]$name) {
    return (($name -split '[-_ ]+') | ForEach-Object {
        if ($_.Length -gt 0) {
            $_.Substring(0, 1).ToUpper() + $_.Substring(1).ToLower()
        }
    }) -join ''
}

$applicationClassName = "$(Convert-ToPascalCase $ServiceName)Application"

$pomContent = @"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.fleet</groupId>
        <artifactId>fleet-platform</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>$ServiceName</artifactId>
    <name>$ServiceName</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
"@

$mainClassContent = @"
package com.fleet.$PackageSuffix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class $applicationClassName {

    public static void main(String[] args) {
        SpringApplication.run($applicationClassName.class, args);
    }
}
"@

$applicationYmlContent = @"
spring:
  application:
    name: $ServiceName
  config:
    import: "optional:configserver:http://localhost:8888"

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
"@

$configRepoYmlContent = @"
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/fleet_db
    username: postgres
    password: fleet123
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: $Port
"@

New-Item -ItemType Directory -Path $javaBasePath -Force | Out-Null
New-Item -ItemType Directory -Path $resourcesPath -Force | Out-Null

foreach ($folder in @("config", "controller", "dto", "entity", "repository", "service")) {
    New-Item -ItemType Directory -Path (Join-Path $javaBasePath $folder) -Force | Out-Null
}

Set-Content -Path (Join-Path $modulePath "pom.xml") -Value $pomContent -Encoding UTF8
Set-Content -Path (Join-Path $javaBasePath "$applicationClassName.java") -Value $mainClassContent -Encoding UTF8
Set-Content -Path (Join-Path $resourcesPath "application.yml") -Value $applicationYmlContent -Encoding UTF8
Set-Content -Path $configFilePath -Value $configRepoYmlContent -Encoding UTF8

[xml]$pomXml = Get-Content -Path $parentPomPath
$projectNode = $pomXml.project
$modulesNode = $projectNode.modules

if (-not $modulesNode) {
    $modulesNode = $pomXml.CreateElement("modules")
    [void]$projectNode.AppendChild($modulesNode)
}

$existingModules = @($modulesNode.module | ForEach-Object { $_.'#text' })
if ($existingModules -notcontains $ServiceName) {
    $newModule = $pomXml.CreateElement("module")
    $newModule.InnerText = $ServiceName
    [void]$modulesNode.AppendChild($newModule)
    $pomXml.Save($parentPomPath)
}

Write-Host "Created module: $ServiceName"
Write-Host "Package: com.fleet.$PackageSuffix"
Write-Host "Port: $Port"
Write-Host "Next step: mvn -pl $ServiceName spring-boot:run"
