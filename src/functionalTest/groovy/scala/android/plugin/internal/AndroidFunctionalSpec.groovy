package scala.android.plugin.internal

class AndroidFunctionalSpec extends FunctionalSpec {

    void createLibBuildFile() {
        initProject()
        // language=groovy
        buildFile << """
           buildscript {
                ext {
                    androidVersion = '4.0.0'
                }
                repositories {
                    google()
                    jcenter()

                }
                dependencies {
                    classpath "com.android.tools.build:gradle:\$androidVersion"
         
                 }
            }
        
            plugins {
                id 'scala.android' apply false
            }
            
            apply plugin: 'com.android.library'
            apply plugin: 'scala.android'
            
            allprojects {
                repositories {
                    google()
                    jcenter()
                }
            }
            
            android {
                compileSdkVersion 28
                buildToolsVersion "29.0.3"
                defaultConfig {              
                    minSdkVersion 28
                    targetSdkVersion 29
                    versionCode 1
                    versionName "1.0"
                    testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
                }

                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_1_8
                    targetCompatibility JavaVersion.VERSION_1_8
                }
                buildTypes {
                    debug {
                        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
                    }
                    release {
                        minifyEnabled true
                        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                    }
              }
            }
            
            tasks.withType(ScalaCompile) {
                 scalaCompileOptions.with{
                    additionalParameters = [
                         '-language:higherKinds'
                    ]
                 }
            }
            
            dependencies {
                implementation fileTree(dir: 'libs', include: ['*.jar'])

                implementation 'androidx.appcompat:appcompat:1.0.2'
                implementation 'com.google.android.material:material:1.0.0'
                implementation 'androidx.constraintlayout:constraintlayout:1.1.3'
                implementation "org.scala-lang:scala-library:2.13.2"
                implementation 'org.typelevel:cats-effect_2.13:2.1.3'
                implementation 'androidx.navigation:navigation-fragment:2.0.0'
                implementation 'androidx.navigation:navigation-ui:2.0.0'
                testImplementation 'junit:junit:4.12'
                androidTestImplementation 'androidx.test.ext:junit:1.1.1'
                androidTestImplementation 'androidx.test.espresso:espresso-core:3.2.0'
            }
        """
    }

    void createAppBuildFile() {
        initProject()
        // language=groovy
        buildFile << """
           buildscript {
                ext {
                    androidVersion = '4.0.0'
                }
                repositories {
                    google()
                    jcenter()

                }
                dependencies {
                    classpath "com.android.tools.build:gradle:\$androidVersion"
         
                 }
            }
        
            plugins {
                id 'scala.android' apply false
            }
            
            apply plugin: 'com.android.application'
            apply plugin: 'scala.android'
            
            allprojects {
                repositories {
                    google()
                    jcenter()
                }
            }
            
            android {
                compileSdkVersion 29
                buildToolsVersion "29.0.3"

                defaultConfig {
                    applicationId "scala.android.test"
                    minSdkVersion 26
                    targetSdkVersion 29
                    versionCode 1
                    versionName "1.0"
                    testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
                }

                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_1_8
                    targetCompatibility JavaVersion.VERSION_1_8
                }
                buildTypes {
                    debug {
                        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
                    }
                    release {
                        minifyEnabled true
                        shrinkResources true
                        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                    }
              }
            }
            
            tasks.withType(ScalaCompile) {
                 scalaCompileOptions.with{
                    additionalParameters = [
                         '-language:higherKinds'
                    ]
                 }
            }
            
            dependencies {
                implementation fileTree(dir: 'libs', include: ['*.jar'])

                implementation 'androidx.appcompat:appcompat:1.0.2'
                implementation 'com.google.android.material:material:1.0.0'
                implementation 'androidx.constraintlayout:constraintlayout:1.1.3'
                implementation "org.scala-lang:scala-library:2.13.2"
                implementation 'org.typelevel:cats-effect_2.13:2.1.3'
                implementation 'androidx.navigation:navigation-fragment:2.0.0'
                implementation 'androidx.navigation:navigation-ui:2.0.0'
                testImplementation 'junit:junit:4.12'
                androidTestImplementation 'androidx.test.ext:junit:1.1.1'
                androidTestImplementation 'androidx.test.espresso:espresso-core:3.2.0'
            }
        """
    }

    void createProguardRules() {
        file('proguard-rules.pro') << ''
    }

    void createAndroidManifest() {
        file('src/main/AndroidManifest.xml') << """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="scala.android.test">
        <application
            android:allowBackup="true"
            android:label="Test App">
          <activity
              android:name=".MainActivity"
              android:label="Test App">
            <intent-filter>
              <action android:name="android.intent.action.MAIN"/>
              <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
          </activity>
        </application>
      </manifest>
    """.trim()
    }

    void createMainActivityLayoutFile() {
        file('src/main/res/layout/activity_main.xml') << """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
      >
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Hello Groovy!"
            android:gravity="center"
            android:textAppearance="?android:textAppearanceLarge"
        />
      </FrameLayout>
    """.trim()
    }


    void createSimpleAndroidManifest() {
        file('src/main/AndroidManifest.xml') << """
     <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="scala.android.test" />
    """.trim()
    }

    private void initProject() {
        createProguardRules()

        testProjectDir.newFile("gradle.properties") << 'android.useAndroidX=true'

        buildFile = testProjectDir.newFile('build.gradle')
    }
}
