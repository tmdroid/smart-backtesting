# smart-backtester

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew run` to launch the JavaFX candle chart app.
* Run `./gradlew build` to only build the application.
* Run `./gradlew test` to run the unit tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

This project follows the suggested multi-module setup and consists of the `app` and `utils` subprojects.
The shared build logic was extracted to a convention plugin located in `buildSrc`.

## Example

```
./gradlew run
```

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies
and both a build cache and a configuration cache (see `gradle.properties`).
