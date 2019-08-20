#!/usr/bin/env bash
java -jar ./target/swagger-diff-1.2.1-jar-with-dependencies.jar -old https://app-stg0.skyy.io/api2/doc/swagger_bundle.json -new http://localhost:8080/nova/v2/api-docs -v 2.0 -output-mode markdown --strict-mode --ignore-file ignore.txt
