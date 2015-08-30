# Gemfury Quickstart
Gemfury is a maven plugin for publishing private artifacts to gemfury repository during the deploy phase.

Add the plugin to your maven pom.
##Maven
```xml
<build>
  <plugins>
    <plugin>
      <groupId>uk.co.solong</groupId>
      <artifactId>gemfury-maven-plugin</artifactId>
      <version>0.0.1-SNAPSHOT</version>
      <executions>
        <execution>
          <id>execution1</id>
          <phase>deploy</phase>
          <configuration>
            <gemfuryUrl>https://SomeSecretToken@repo.fury.io/youraddress/</gemfuryUrl>
          </configuration>
          <goals>
            <goal>gemfury</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Run the deploy goal like you normally would.

All done!
