package scala.android.plugin


import scala.android.plugin.internal.AndroidFunctionalSpec

class GradleTaskSpec extends AndroidFunctionalSpec {

    def "test android app"() {
        given:
        file("settings.gradle") << "rootProject.name = 'test-app'"
        createAppBuildFile()
        createAndroidManifest()
        createMainActivityLayoutFile()

        // create Java class to ensure this compile correctly along with scala classes
        file('src/main/java/scala/android/test/SimpleJava.java') << """
            package scala.android.test;
            
            public class SimpleJava {
                public static int getInt() {
                    return 1337;
                }
            }
        """

        file('src/main/java/scala/android/test/SimpleScala.scala') << """
            package scala.android.test;
            
             object SimpleScala {
                def getInt():Int = {
                    return 1337;
                }
            }
        """

        file('src/main/scala/scala/android/test/MainActivity.scala') << """
            package scala.android.test
            import android.app.Activity
            import android.os.Bundle
            import cats.effect.IO
         
            class MainActivity extends Activity {
                override def onCreate(savedInstanceState:Bundle):Unit = {
                    super.onCreate(savedInstanceState)
                    val program = IO {
                        val contentView = R.layout.activity_main
                        val someValue = SimpleJava.getInt()
                        val result = someValue * SimpleJava.getInt()
                    }
                    
                    program.unsafeRunSync()
                }
            }
        """

        file('src/test/scala/scala/android/test/SimpleScalaTest.scala') << """
            package scala.android.test
            import org.junit.Test
            import org.junit.Assert._
       
            
            class SimpleScalaTest {
                @Test def shouldCompile():Unit = {
                    assertEquals(1337, SimpleScala.getInt())
                }
            }
        """

        file('src/test/scala/scala/android/test/JvmTest.scala') << """
            package scala.android.test
            import org.junit.Test
            import org.junit.Assert._
            import cats.effect.IO
            class JvmTest {
                @Test def shouldCompile():Unit = {
                    var program = IO {
                         assertEquals(10*2, 20)
                    }
                    program.unsafeRunSync()
                }
            }
        """

        when:
        run( 'test')

        then:
        noExceptionThrown()
        file("build/intermediates/javac/debug/classes/scala/android/test/SimpleJava.class").exists()
        file("build/intermediates/javac/debug/classes/scala/android/test/SimpleScala.class").exists()
        file("build/intermediates/javac/debug/classes/scala/android/test/MainActivity.class").exists()
        file("build/intermediates/javac/debugUnitTest/classes/scala/android/test/JvmTest.class").exists()
        file("build/intermediates/javac/releaseUnitTest/classes/scala/android/test/JvmTest.class").exists()
        file("build/intermediates/javac/debugUnitTest/classes/scala/android/test/SimpleScalaTest.class").exists()
        file("build/intermediates/javac/releaseUnitTest/classes/scala/android/test/SimpleScalaTest.class").exists()
    }

    def "assemble android app"() {
        given:
        file("settings.gradle") << "rootProject.name = 'test-app'"
        createAppBuildFile()
        createAndroidManifest()
        createMainActivityLayoutFile()

        // create Java class to ensure this compile correctly along with scala classes
        file('src/main/java/scala/android/test/SimpleJava.java') << """
            package scala.android.test;
            
            public class SimpleJava {
                public static int getInt() {
                    return 1337;
                }
            }
        """

        file('src/main/scala/scala/android/test/MainActivity.scala') << """
            package scala.android.test
            import android.app.Activity
            import android.os.Bundle
            import cats.effect.IO
         
            class MainActivity extends Activity {
                override def onCreate(savedInstanceState:Bundle):Unit = {
                    super.onCreate(savedInstanceState)
                    val program = IO {
                        val contentView = R.layout.activity_main
                        val someValue = SimpleJava.getInt()
                        val result = someValue * SimpleJava.getInt()
                    }
                    
                    program.unsafeRunSync()
                }
            }
        """

        file('src/test/scala/scala/android/test/JvmTest.scala') << """
            package scala.android.test
            import org.junit.Test
            import org.junit.Assert._
            import cats.effect.IO
            class JvmTest {
                @Test def shouldCompile():Unit = {
                    var program = IO {
                         assertEquals(10*2, 20)
                    }
                    program.unsafeRunSync()
                }
            }
        """

        when:
        run('assemble', 'test')

        then:
        noExceptionThrown()
        file("build/outputs/apk/debug/test-app-debug.apk").exists()
        file("build/outputs/apk/release/test-app-release-unsigned.apk").exists()
        file("build/intermediates/javac/debug/classes/scala/android/test/SimpleJava.class").exists()
        file("build/intermediates/javac/debug/classes/scala/android/test/MainActivity.class").exists()
        file("build/intermediates/javac/debugUnitTest/classes/scala/android/test/JvmTest.class").exists()
        file("build/intermediates/javac/releaseUnitTest/classes/scala/android/test/JvmTest.class").exists()
    }

    def "assemble android library"() {
        given:
        file("settings.gradle") << "rootProject.name = 'test-lib'"
        createLibBuildFile()
        createSimpleAndroidManifest()

        // create Java class to ensure this compile correctly along with scala classes
        file('src/main/java/scala/android/test/SimpleJava.java') << """
            package scala.android.test;
            
            public class SimpleJava {
                public static int getInt() {
                    return 1337;
                }
            }
        """

        file('src/main/scala/scala/android/test/SimpleScala.scala') << """
            package scala.android.test
            import android.app.Activity
            import android.os.Bundle
            import cats.effect.IO
         
            class SimpleScala {
                def add(x:Int, y:Int):IO[Int] = {
                    val program = IO {
                        x + y
                    }
                    
                    program
                }
            }
        """

        file('src/test/scala/scala/android/test/JvmTest.scala') << """
            package scala.android.test
            import org.junit.Test
            import org.junit.Assert._
            import cats.effect.IO
            class JvmTest {
                @Test def shouldCompile():Unit = {
                    var program = IO {
                         assertEquals(10*2, 20)
                    }
                    program.unsafeRunSync()
                }
            }
        """

        when:
        run('assemble', 'test')

        then:
        noExceptionThrown()
        file('build/outputs/aar/test-lib-debug.aar').exists()
        file('build/outputs/aar/test-lib-release.aar').exists()
        file("build/intermediates/javac/debug/classes/scala/android/test/SimpleJava.class").exists()
        file("build/intermediates/javac/debug/classes/scala/android/test/SimpleScala.class").exists()
        file("build/intermediates/javac/debugUnitTest/classes/scala/android/test/JvmTest.class").exists()
        file("build/intermediates/javac/releaseUnitTest/classes/scala/android/test/JvmTest.class").exists()
    }
}
