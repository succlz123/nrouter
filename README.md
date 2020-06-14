## NRouter

## Usage

~~~
    repositories {
        google()
        jcenter()
        maven { url 'https://jitpack.io' }
    }
~~~

~~~
    dependencies {
        classpath 'com.android.tools.build:gradle:4.0.0'
        classpath 'com.github.succlz123.nrouter:plugin:0.0.1'
    }
~~~

~~~
    apply plugin: 'org.succlz123.nrouter'
~~~

~~~
    kapt 'com.github.succlz123.nrouter:processor:0.0.1'
    implementation 'com.github.succlz123.nrouter:lib:0.0.1'
~~~


### Sample

~~~ java
@Path(path = "/app/activity/first")
public class FirstActivity extends BaseActivity {
}
~~~

~~~ java
NRouter.path("/app/activity/first").with("params", "123").open(MainActivity.this);
~~~


## APT

### Debug

gradle.properties
~~~
org.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
~~~

~~~
Click Annotation Debug Button
~~~

~~~
Rebuild Project
~~~

## Plugin

### Debug

~~~
./gradlew --no-daemon clean :app:assemble -Dorg.gradle.debug=true
~~~

### Upload

> upload to local repo

~~~
./gradlew :nrouter-plug:uploadArchives
~~~

> upload to gradle repo

~~~
https://plugins.gradle.org/docs/submit
~~~

~~~
./gradlew publishPlugins
~~~
